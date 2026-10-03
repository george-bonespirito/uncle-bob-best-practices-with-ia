# Import assíncrono (202 Accepted) com tabela própria de status do lote

O endpoint de bulk import recebe planilhas de até ~50.000 linhas (denormalizadas: cabeçalho do pedido repetido em cada linha de item — `source_system_code`, `source_order_number`, `customer_id`, `order_created_at`, `sku`, `quantity`). Processar isso dentro da própria requisição HTTP arriscaria timeout de cliente/proxy em requests de minutos.

Decidimos: `POST /imports/orders` aceita o arquivo e devolve `202 Accepted` com um `importBatchId` imediatamente; o processamento roda em background usando virtual threads (uma por linha/lote); `GET /imports/orders/{importBatchId}` consulta status e resultado.

Isso exige uma tabela `import_batches` própria — o status do lote (`PENDING`/`PROCESSING`/`COMPLETED`/`COMPLETED_WITH_ERRORS`) e os contadores (`total_rows`, `processed_rows`, `orders_created`) existem *antes* de qualquer `Order` ser criado, então não cabem só como campo em `orders`. Erros de linha (duplicata de `source_order_number`, dado inválido) vão em `import_batch_row_errors` (tabela separada, não array embutido na resposta — um lote ruim pode ter milhares de erros, inviável devolver tudo inline numa resposta HTTP).

Alternativa rejeitada: processamento síncrono, resposta só ao final. Mediria o ganho de virtual thread diretamente pelo tempo de resposta HTTP, mas é padrão ruim de API pra job de minutos — foi a opção não escolhida deliberadamente, priorizando uma API bem-comportada sobre medição mais direta (o throughput de virtual thread ainda é medível via log/métrica no processamento em background).

Duplicata de `(source_system_code, source_order_number)` — já existente em outro lote ou no próprio — é tratada como erro de linha (`import_batch_row_errors`), não bloqueia o resto do lote. Consistente com o tratamento de linha inválida já decidido (`bulk_import.feature`, TC-02).

**Nota técnica — parsing do `.xlsx`:** com até 50.000 linhas, o leitor DOM padrão do Apache POI (`XSSFWorkbook`) carrega o arquivo inteiro em memória — evitar. Usar a API de streaming do POI (`XSSFReader`/SAX) pra ler linha a linha sem materializar o workbook inteiro, despachando cada linha pra uma virtual thread processar (parse + validação + insert). Isso é o que de fato exercita virtual threads em volume real — não a leitura do arquivo em si, que continua sequencial.

Status: accepted

## Revisão (2026-10-03) — `PROCESSING` passa a ser usado e o lote ganha `FAILED`

O corpo desta ADR listava `PROCESSING`, mas o código ia direto de `PENDING` para `COMPLETED`/`COMPLETED_WITH_ERRORS`. Pior: qualquer exceção no processamento em background (banco recusando um insert, conexão caindo) era engolida pelo `Future` descartado do executor de virtual threads, e o lote ficava `PENDING` para sempre — sem log, sem estado terminal, e com o mesmo arquivo recusado em `409` no reenvio.

Agora o lote vai a `PROCESSING` ao começar o processamento e, se algo falhar fora do tratamento de linha, termina em **`FAILED`** (terminal, com `completed_at` preenchido), com o erro registrado no log junto do `importBatchId`. Erro de linha continua sendo `import_batch_row_errors` + `COMPLETED_WITH_ERRORS` — `FAILED` é só para falha que impede o lote de terminar. Não foi preciso migration: `status` é `VARCHAR(30)` sem `CHECK` (V1).

Fica de fora desta revisão, de propósito: o que acontece com Orders já gravados antes da falha (o processamento não é transacional), se um lote `FAILED` libera o Import Fingerprint para reenvio, e lote órfão em `PROCESSING` quando a própria JVM cai no meio (exige varredura por tempo, não `try/catch`).

## Revisão (2026-10-03) — processamento numa transação, duplicata por chave, corrida no fingerprint

Três pontos do `ImportOrdersService` que a revisão anterior deixou de fora ou não cobria:

- **Lote que vai a `FAILED` não deixa dado pela metade.** Orders, `import_batch_row_errors` e o fechamento do lote (`COMPLETED`/`COMPLETED_WITH_ERRORS` + contadores) são gravados numa **única transação**; se ela falha, volta inteira. `PROCESSING` é gravado antes, em transação própria (fica visível durante o processamento), e `FAILED` depois, em outra transação — senão o rollback levaria o próprio status de falha junto. A fronteira é `TransactionOperations` (o `TransactionTemplate` do Spring Boot) injetado no serviço, não `@Transactional`: o processamento roda no executor, chamado de dentro da própria classe, e `@Transactional` em autoinvocação não passa pelo proxy.
- **Duplicata de `(source_system_code, source_order_number)` é consultada por chave**, não carregando `orders` inteira: só as chaves do lote, agrupadas por `source_system_code` (coluna líder do `UNIQUE` de V2) e em blocos de até 1.000 `source_order_number`, abaixo do limite de parâmetros por statement do PostgreSQL. A decisão continua na função pura `partitionByDuplicate` (`domain.policy`, ADR order/0006).
- **Dois uploads simultâneos do mesmo arquivo dão `202` + `409`, não `500`.** Os dois passam juntos pela consulta de Import Fingerprint e o segundo `INSERT` bate no `uq_import_batches_file_fingerprint` (V4). A violação de integridade é traduzida para a mesma recusa de reenvio quando a releitura por fingerprint encontra o lote vencedor; se não encontra, a violação é outra e sobe.

Continua fora: lote órfão em `PROCESSING` se a JVM cai (varredura por tempo) e se um lote `FAILED` libera o fingerprint para reenvio. Custo aceito: o lote inteiro vive num único contexto de persistência até o commit — para o teto de ~50.000 linhas desta ADR é aceitável; se não for, o próximo passo é flush/clear por bloco dentro da mesma transação.
