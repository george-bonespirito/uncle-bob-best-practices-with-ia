# Import assíncrono (202 Accepted) com tabela própria de status do lote

O endpoint de bulk import recebe planilhas de até ~50.000 linhas (denormalizadas: cabeçalho do pedido repetido em cada linha de item — `source_system_code`, `source_order_number`, `customer_id`, `order_created_at`, `sku`, `quantity`). Processar isso dentro da própria requisição HTTP arriscaria timeout de cliente/proxy em requests de minutos.

Decidimos: `POST /imports/orders` aceita o arquivo e devolve `202 Accepted` com um `importBatchId` imediatamente; o processamento roda em background usando virtual threads (uma por linha/lote); `GET /imports/orders/{importBatchId}` consulta status e resultado.

Isso exige uma tabela `import_batches` própria — o status do lote (`PENDING`/`PROCESSING`/`COMPLETED`/`COMPLETED_WITH_ERRORS`) e os contadores (`total_rows`, `processed_rows`, `orders_created`) existem *antes* de qualquer `Order` ser criado, então não cabem só como campo em `orders`. Erros de linha (duplicata de `source_order_number`, dado inválido) vão em `import_batch_row_errors` (tabela separada, não array embutido na resposta — um lote ruim pode ter milhares de erros, inviável devolver tudo inline numa resposta HTTP).

Alternativa rejeitada: processamento síncrono, resposta só ao final. Mediria o ganho de virtual thread diretamente pelo tempo de resposta HTTP, mas é padrão ruim de API pra job de minutos — foi a opção não escolhida deliberadamente, priorizando uma API bem-comportada sobre medição mais direta (o throughput de virtual thread ainda é medível via log/métrica no processamento em background).

Duplicata de `(source_system_code, source_order_number)` — já existente em outro lote ou no próprio — é tratada como erro de linha (`import_batch_row_errors`), não bloqueia o resto do lote. Consistente com o tratamento de linha inválida já decidido (`bulk_import.feature`, TC-02).

**Nota técnica — parsing do `.xlsx`:** com até 50.000 linhas, o leitor DOM padrão do Apache POI (`XSSFWorkbook`) carrega o arquivo inteiro em memória — evitar. Usar a API de streaming do POI (`XSSFReader`/SAX) pra ler linha a linha sem materializar o workbook inteiro, despachando cada linha pra uma virtual thread processar (parse + validação + insert). Isso é o que de fato exercita virtual threads em volume real — não a leitura do arquivo em si, que continua sequencial.

Status: accepted
