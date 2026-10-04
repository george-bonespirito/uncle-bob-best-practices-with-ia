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

Consequência do desenho, não bug: dois arquivos **diferentes** com a mesma chave de Order `(source_system_code, source_order_number)` importados ao mesmo tempo passam juntos pela consulta de duplicata, e o perdedor bate no `UNIQUE` de V2 no commit — vai **inteiro** a `FAILED` (rollback completo, nenhum Order dele fica gravado), não a `COMPLETED_WITH_ERRORS` com só a linha repetida recusada.

## Revisão (2026-10-04): reenvio de arquivo com lote FAILED (architect-000143)

As duas revisões anteriores deixaram de fora, de propósito, se um lote `FAILED` libera o Import Fingerprint. Hoje não libera: o `uq_import_batches_file_fingerprint` (V4) aceita um lote por arquivo, então o mesmo arquivo recebe `409` para sempre depois de uma falha que não é dele (banco fora do ar, conexão caindo), mesmo sem nenhum Order gravado, já que o lote `FAILED` volta inteiro desde a revisão anterior. Decisão do George (2026-10-04, resposta ao `dev.uncle-bob-000059`): **"sim, pode ser reenviado"**.

**Decisão.** O reenvio de um arquivo cujo lote terminou `FAILED` cria um **lote novo**: `202`, `importBatchId` novo. O lote `FAILED` fica no banco como está, com `status` e `completed_at` intactos, como histórico. Lote em `PENDING`, `PROCESSING`, `COMPLETED` ou `COMPLETED_WITH_ERRORS` continua bloqueando o reenvio com `409` e o `importBatchId` dele. Reabrir o lote `FAILED` (voltar para `PENDING`) foi descartado: apagaria o registro da falha e misturaria duas tentativas num lote só.

**Como o banco garante.** Uma coluna nova, `active_fingerprint VARCHAR(64)` e anulável, guarda o Import Fingerprint enquanto o lote bloqueia reenvio. Ela fica `NULL` quando o lote vai a `FAILED`. O `UNIQUE` passa de `file_fingerprint` para `active_fingerprint`. Como `NULL` não colide em `UNIQUE`, cabem quantos lotes `FAILED` forem preciso por arquivo e no máximo um lote que bloqueia. `file_fingerprint` continua `NOT NULL` e imutável: é o hash do arquivo, em todo lote, inclusive nos `FAILED`. Migration nova, **`V5__active_fingerprint_reenvio_apos_failed.sql`**. A V4 não é editada:

```sql
ALTER TABLE import_batches ADD COLUMN active_fingerprint VARCHAR(64);
UPDATE import_batches SET active_fingerprint = file_fingerprint WHERE status <> 'FAILED';
ALTER TABLE import_batches ADD CONSTRAINT uq_import_batches_active_fingerprint UNIQUE (active_fingerprint);
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_active_fingerprint CHECK (
    (status = 'FAILED' AND active_fingerprint IS NULL)
    OR (status <> 'FAILED' AND active_fingerprint IS NOT NULL AND active_fingerprint = file_fingerprint)
);
ALTER TABLE import_batches DROP CONSTRAINT uq_import_batches_file_fingerprint;
```

- **Backfill sem conflito:** a V4 garantia um lote por fingerprint, então copiar `file_fingerprint` para os lotes que não estão `FAILED` não viola o `UNIQUE` novo. Lotes `FAILED` que já existem ficam liberados para reenvio assim que a V5 roda, o que é coerente com a decisão.
- **O `CHECK` amarra `active_fingerprint` ao `status`:** um lote `FAILED` com fingerprint ativo, ou um lote que bloqueia com `active_fingerprint` nulo ou diferente do `file_fingerprint`, é recusado pelo banco. O `IS NOT NULL` explícito é necessário. Sem ele, `active_fingerprint = file_fingerprint` com `NULL` dá *unknown*, e `CHECK` com *unknown* passa: um lote `PENDING` com `active_fingerprint` nulo entraria e não bloquearia nada. Medido nos dois bancos. É o primeiro `CHECK` que cita um valor de `status`; a V1 não tem `CHECK` no `status`, e isso continua igual.
- **Índice de `file_fingerprint`:** sai junto com o `UNIQUE` da V4. Nenhuma consulta passa a filtrar por `file_fingerprint`; a consulta de reenvio vai por `active_fingerprint`, que tem o índice do `UNIQUE`. Se aparecer consulta de histórico por arquivo, o índice entra quando ela entrar.

**No código** (revisão aceita em architect-000154):

- O `ImportBatch` nasce com `activeFingerprint = uploadedFile.fingerprint`, e `fail()` zera o campo junto com `status` e `completed_at`. A regra "`FAILED` libera o arquivo" mora no agregado, não no serviço.
- O `markFailed`, que roda fora da transação do lote (revisão anterior), não muda: continua sendo `findById` + `fail()` + `save`, que vira um único `UPDATE` da linha com `status`, `completed_at` e `active_fingerprint = NULL` juntos.
- A consulta de reenvio do `ImportOrdersService` deixa de ser "existe lote com este fingerprint" e passa a ser "existe lote que **bloqueia** este fingerprint": o port `ImportBatchRepository` troca `findByUploadedFileFingerprint` por `findBlockingByFingerprint`, que o adapter resolve por `active_fingerprint`.
- A tradução da corrida (revisão anterior) continua com a mesma forma. Dois reenvios simultâneos depois de um `FAILED` passam juntos pela consulta (nenhum lote bloqueia), os dois tentam o `INSERT` com o mesmo `active_fingerprint`, e o perdedor bate no `uq_import_batches_active_fingerprint`. A `DataIntegrityViolationException` vira `409` quando a releitura acha o lote vencedor; se não acha, vale o retry único descrito nas consequências. No PostgreSQL, o `INSERT` do perdedor espera o commit do vencedor antes de falhar (a releitura em `READ COMMITTED` já vê o vencedor). No H2, a violação sai na hora ou no commit. Nos dois, é um lote novo, nunca dois.

**H2 e PostgreSQL.** A solução não depende de nada que falte no H2. Medido com JDBC, aplicando o texto de V1, V3, V4 e da V5 acima no H2 2.2.224 (o dos testes, `jdbc:h2:mem:order;MODE=PostgreSQL`) e num PostgreSQL 17.11 descartável:

- **V5:** roda igual nos dois.
- **`NULL` no `UNIQUE`:** não colide em nenhum dos dois. O H2 reporta `NULLS_DISTINCT=YES` para o constraint mesmo em `MODE=PostgreSQL`; no PostgreSQL, `NULLS DISTINCT` é o padrão.
- **Bloqueio:** o reenvio de arquivo `FAILED` entra. Segundo lote ativo e reenvio de arquivo com lote `PENDING`, `PROCESSING`, `COMPLETED` ou `COMPLETED_WITH_ERRORS` dão `23505`.
- **`CHECK`:** recusa as três combinações inválidas. O H2 usa SQLState `23513` e o PostgreSQL `23514`; o Spring traduz os dois para `DataIntegrityViolationException`.
- **Corrida:** duas conexões inserindo o mesmo `active_fingerprint` dão um commit e um `23505`, sobrando um `FAILED` e um `PENDING`.

O teste do serviço roda sobre a mesma V5 no H2 via Flyway (`ddl-auto: validate` confere o mapeamento), então ele prova no H2 o mesmo constraint que vale no PostgreSQL. A evidência contra PostgreSQL em container continua com o QA.

**Alternativas consideradas:**

- **Índice único parcial** (`CREATE UNIQUE INDEX ... ON import_batches (file_fingerprint) WHERE status <> 'FAILED'`). É o mais direto no PostgreSQL e não pede coluna nova. Rejeitado porque o H2 não aceita `WHERE` em índice (erro de sintaxe, medido). Exigiria migration por banco (`db/migration/{vendor}`), e o teste no H2 não provaria o constraint que roda em produção, justamente o que segura a corrida.
- **Coluna gerada** (`active_fingerprint GENERATED ALWAYS AS (CASE WHEN status <> 'FAILED' THEN file_fingerprint END)`). Tem fonte única de verdade, sem o código zerar nada. Rejeitada porque o DDL não é portável: o PostgreSQL exige `STORED` e o H2 recusa `STORED` (os dois medidos). Voltaria à migration por banco, e o Hibernate precisaria ler de volta um valor gerado no `INSERT`. Com coluna comum mais o `CHECK`, a sincronia também fica garantida pelo banco.
- **Reabrir o lote `FAILED`** (voltar para `PENDING`, mesmo `importBatchId`). Não mexe no schema. Rejeitada porque perde o histórico da falha (`status` e `completed_at` sobrescritos), fica ambígua para quem guardou o `importBatchId` antigo e contraria o aceite (lote novo, `FAILED` preservado).
- **Alterar o `file_fingerprint` do lote `FAILED`** (sufixo ou `NULL`). Reaproveita o `UNIQUE` da V4. Rejeitada porque `file_fingerprint` deixa de dizer qual arquivo o lote recebeu, quebra o `NOT NULL` da V4 e mistura "hash do arquivo" com "estado de bloqueio" numa coluna só.
- **Tabela de trava** (`import_fingerprint_locks`, chave = fingerprint, apagada no `FAILED`). Funciona nos dois bancos. Rejeitada porque são duas linhas para manter coerentes, com o `markFailed` passando a escrever em duas tabelas, para a mesma garantia de uma coluna com `UNIQUE`.
- **Só checagem na aplicação** (sem constraint, ou `SELECT ... FOR UPDATE` / advisory lock). Rejeitada: sem constraint, a corrida cria dois lotes. `FOR UPDATE` não trava linha que ainda não existe, e advisory lock é do PostgreSQL e não existe no H2.

**Consequências:**

- **Lote preso em `PROCESSING` continua bloqueando o reenvio.** Isso vale quando a JVM cai no meio do processamento ou quando o próprio `markFailed` falha (o caso já logado em dobro). O `active_fingerprint` só é zerado no `FAILED`. A varredura por tempo de lotes órfãos continua **fora do escopo**, como nas revisões anteriores.
- **O lote `FAILED` é preservado.** Um arquivo pode acumular vários lotes `FAILED` e, no máximo, um lote ativo. Pelo `GET /imports/orders/{importBatchId}`, cada tentativa tem seu próprio status. O `409` devolve o lote que bloqueia, nunca um `FAILED`.
- **Risco residual da corrida:** se o lote vencedor chegar a `FAILED` entre o `INSERT` do perdedor e a releitura (milissegundos, e só com arquivo que falha antes de processar qualquer linha), a releitura não acha lote que bloqueia e a violação sobe como `500`, igual a "violação de outra coisa" hoje. Não fica como `500`: **retry único aceito em architect-000154**. Quando a releitura não acha lote que bloqueia, o `INSERT` é tentado exatamente mais uma vez, sem laço. Se a segunda tentativa também violar, seja de novo o fingerprint ou outro constraint, a violação sobe.
- **PRD:** a linha do `409` passa a dizer que lote `FAILED` não bloqueia o reenvio. A descrição de `file_fingerprint` na tabela de `import_batches` (hoje "`UNIQUE`") passa para `active_fingerprint`.
