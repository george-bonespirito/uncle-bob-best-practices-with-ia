# Order Processing (Pedidos de Compra)

Contexto de processamento de pedidos de compra num cenário de supply chain: um Order é criado a partir de uma importação em massa (planilha) e passa por múltiplas Processing Steps independentes até ser concluído.

## Language

**Order**:
Pedido de compra. Entidade central do contexto — criada a partir de uma importação em massa (planilha .xlsx/.csv) e evolui através de uma sequência de Processing Steps até ser concluída.
_Avoid_: Purchase, Pedido (sem qualificador), Compra

**Processing Step**:
Uma das operações que atua sobre um Order, modificando apenas o subconjunto de campos que ela possui — nunca sobrescreve o Order inteiro. São cinco, e executam **em sequência** dentro de um mesmo Order: Data Enrichment, Credit Validation, Stock Validation, Route Calculation, Invoice Generation. A sequência vale por pedido, não no sistema — há N Orders em voo ao mesmo tempo. Ver [[0008-sequential-processing-steps-per-order]].
_Avoid_: Processo (isolado, sem contexto), Etapa (isolado, sem contexto)

**Data Enrichment**:
Primeira Processing Step, e a única que não é atômica: internamente é um fork-join de 3 tasks concorrentes — Customer Enrichment, Product Enrichment e Pricing — uma por microsserviço de origem, independentes entre si. É onde o Lost Update acontece, e a única etapa em que um Order pode ficar `AWAITING_MASTER_DATA`.
_Avoid_: enriquecimento (isolado), Customer Enrichment (é só uma das 3 tasks, não a etapa inteira)

**Lost Update**:
Situação em que duas escritas concorrentes leem o mesmo Order, cada uma modifica seu próprio campo em memória, e a que salva por último sobrescreve a mudança da outra por ter salvado o objeto inteiro em vez de só o campo que possuía. Acontece entre as 3 tasks da Data Enrichment — que escrevem `customer_snapshot` no Order e `product_snapshot`/`unit_price` nos Order Items, todas incrementando o mesmo `version` do agregado. É o problema central de concorrência deste contexto — ver [[0001-optimistic-locking-for-order-writes]].
_Avoid_: race condition (genérico demais — Lost Update é o caso específico que temos aqui)

**Order Status**:
Estado do Order no ciclo de vida das Processing Steps. Valores: `PENDING` (criado, workflow ainda não disparado), `PROCESSING` (percorrendo a sequência de Processing Steps, uma de cada vez), `AWAITING_MASTER_DATA` (Data Enrichment travada por cliente ou produto que não existe no cadastro — espera indefinida até alguém cadastrar o dado e reexecutar a task; nada foi reservado nesse ponto, ver [[0011-async-only-enrichment-await-master-data]]), `INVOICING` (as 4 anteriores concluídas com sucesso, Invoice Generation em execução), `COMPLETED` (nota fiscal emitida, pedido concluído), `COMPENSATING` (uma Processing Step falhou permanentemente, ações compensatórias em andamento), `CANCELLED` (compensação concluída com sucesso), `COMPENSATION_FAILED` (uma ação compensatória falhou — estado terminal que exige intervenção manual, nunca fica em retry infinito).
_Avoid_: status genérico tipo "ACTIVE"/"DONE" sem esses valores canônicos

**Hard-blocking Step**:
Processing Step cuja falha permanente impede o Order de avançar para Invoice Generation — hoje as 4 (Enrichment, Credit Validation, Stock Validation, Route Calculation) são todas hard-blocking, mesmo as que não têm ação compensatória. Cadastro faltando não é falha permanente: leva a `AWAITING_MASTER_DATA`, que é espera, não cancelamento.
_Avoid_: required step, mandatory step

**Compensating Action**:
Ação executada por uma Processing Step compensatória (hoje: Credit Validation, Stock Validation) para desfazer seu próprio efeito colateral quando o Order é cancelado. Executada pelo mesmo MS dono do efeito colateral, sobre sua própria base — nunca por outro serviço. Ver [[0002-compensating-flow-for-order-processing]] (ADR raiz).
_Avoid_: rollback (termo genérico demais fora do contexto de transação de banco), undo

**Order Item**:
Uma linha de produto dentro de um Order — SKU, quantidade, preço unitário, dado de produto. O Order é o aggregate root; Order Item nunca existe fora de um Order, e a concorrência (`version`) é do Order inteiro, não por item — ver [[0001-optimistic-locking-for-order-writes]].
_Avoid_: line item (isolado, sem o resto do vocabulário em inglês do contexto), produto do pedido

**Fulfillment Status**:
Situação de atendimento de um Order Item — `FULFILLED` ou `NOT_FULFILLED`, com motivo quando não atendido (hoje só `PRODUCT_INACTIVE`). Existe porque um pedido pode ser atendido em parte: produto descontinuado não recusa o pedido inteiro, deixa aquele item de fora — ver [[0012-partial-fulfillment-for-inactive-products]]. É a única situação que vive no item; status de processamento é do Order.
_Avoid_: status do item (ambíguo com o status do Order), item cancelado, item rejeitado (rejeição é da linha da planilha no import, coisa diferente — ver Rejected Order Row)

**Order Total**:
Soma de `unit_price × quantity` dos Order Items `FULFILLED`. **Derivado, nunca armazenado** — item pode virar `NOT_FULFILLED` depois da precificação, inclusive no faturamento, e um total gravado ficaria errado em silêncio nesses casos. É o valor que a Credit Validation submete e o que a fatura cobra — ver [[0013-order-total-derived-and-price-frozen-at-enrichment]].
_Avoid_: total_amount (não existe como coluna), valor do pedido (ambíguo entre com e sem itens não atendidos), subtotal

**Partial Fulfillment**:
Order que segue o fluxo com parte dos seus Order Items marcados `NOT_FULFILLED`. As etapas seguintes operam sobre os itens atendidos — crédito, estoque, carga e fatura valem sobre o total reduzido. Order sem nenhum item atendido não é Partial Fulfillment: é cancelado.
_Avoid_: pedido parcial, entrega parcial (é do Route/entrega, não deste contexto), backorder (implica atender depois, que não existe aqui)

**Snapshot**:
Cópia denormalizada de dado de outro bounded context (Customer, Product, preço), gravada no Order/Order Item no momento da Data Enrichment. Não é fonte de verdade — [Customers](../customers/CONTEXT.md)/Products continuam donos do dado real; o snapshot só existe pra o Order não precisar consultar outro MS depois de enriquecido. Carrega dado de negócio do pedido (nome, documento, endereço de entrega, Name e Category do produto) — nunca a Description do produto, que fica no [Products](../products/CONTEXT.md), nem dado de contato, que precisa estar atualizado no momento de uso e por isso é consultado direto no dono.
_Avoid_: cache (implica invalidação/expiração, que não existe aqui — snapshot é permanente, tirado uma vez; e o Order não mantém cache de dado de outro contexto)

**Import Batch**:
Um upload de planilha de import em massa — existe como entidade própria (`PENDING`/`PROCESSING`/`COMPLETED`/`COMPLETED_WITH_ERRORS`) antes de qualquer Order ser criado a partir dele. Planilha é denormalizada: cabeçalho do pedido (`source_system_code`, `source_order_number`, `customer_id`, `order_created_at`) repetido em toda linha de item. Ver [[0004-async-bulk-import-with-batch-status]].
_Avoid_: import job (genérico, "batch" já é o termo canônico aqui), upload (é só o gatilho, não a entidade)

**Import Fingerprint**:
Hash do conteúdo do arquivo de import, calculado no upload. Arquivo já importado é recusado na hora, sem criar Import Batch. Camada distinta do Source Order Number: o fingerprint pega o reenvio do mesmo arquivo, o Source Order Number pega o pedido repetido entre arquivos diferentes.
_Avoid_: checksum, hash do arquivo (sem qualificar o papel), deduplicação de upload

**Rejected Order Row**:
Linha da planilha recusada na validação do import, registrada como erro do Import Batch enquanto as demais linhas seguem; o Import Batch fecha em `COMPLETED_WITH_ERRORS`. Só o que dá pra verificar dentro do próprio arquivo produz uma Rejected Order Row — dado obrigatório ausente, duplicata de Source Order Number. Existência de cliente ou de produto **não** é verificada aqui: o import nunca consulta outro contexto, e dado de cadastro faltando é resolvido pela Processing Step, não recusando a linha — ver [[0011-async-only-enrichment-await-master-data]].
_Avoid_: linha inválida (genérico), erro de import, pedido rejeitado (ambíguo com falha de Processing Step)

**Source Order Number**:
Chave natural do Order no sistema de origem (`source_system_code` + `source_order_number`) — não é o `id` interno do Order, é o identificador que o sistema que gerou o pedido usa. Par único, usado pra detectar duplicata entre lotes de import.
_Avoid_: order number (sem qualificar "source", ambíguo com o `id` interno)
