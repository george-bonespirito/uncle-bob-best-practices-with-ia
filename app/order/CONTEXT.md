# Order Processing (Pedidos de Compra)

Contexto de processamento de pedidos de compra num cenário de supply chain: um Order é criado a partir de uma importação em massa (planilha) e passa por múltiplas Processing Steps independentes até ser concluído.

## Language

**Order**:
Pedido de compra. Entidade central do contexto — criada a partir de uma importação em massa (planilha .xlsx/.csv) e evolui através de múltiplas Processing Steps concorrentes até ser concluída.
_Avoid_: Purchase, Pedido (sem qualificador), Compra

**Processing Step**:
Uma das operações independentes que atua sobre um Order, modificando apenas o subconjunto de campos que ela possui — nunca sobrescreve o Order inteiro. Etapas identificadas até agora: Data Enrichment, Credit Validation, Stock Validation, Route Calculation, Invoice Generation.
_Avoid_: Processo (isolado, sem contexto), Etapa (isolado, sem contexto)

**Lost Update**:
Situação em que duas Processing Steps concorrentes leem o mesmo Order, cada uma modifica seu próprio campo em memória, e a que salva por último sobrescreve a mudança da outra por ter salvado o objeto inteiro em vez de só o campo que possuía. É o problema central de concorrência deste contexto — ver [[0001-optimistic-locking-for-order-writes]].
_Avoid_: race condition (genérico demais — Lost Update é o caso específico que temos aqui)

**Order Status**:
Estado do Order no ciclo de vida das Processing Steps. Valores: `PENDING` (criado, workflow ainda não disparado), `PROCESSING` (Enrichment, Credit Validation, Stock Validation e Route Calculation em execução concorrente), `INVOICING` (as 4 anteriores concluídas com sucesso, Invoice Generation em execução), `COMPLETED` (nota fiscal emitida, pedido concluído), `COMPENSATING` (uma Processing Step falhou permanentemente, ações compensatórias em andamento), `CANCELLED` (compensação concluída com sucesso), `COMPENSATION_FAILED` (uma ação compensatória falhou — estado terminal que exige intervenção manual, nunca fica em retry infinito).
_Avoid_: status genérico tipo "ACTIVE"/"DONE" sem esses valores canônicos

**Hard-blocking Step**:
Processing Step cuja falha permanente impede o Order de avançar para Invoice Generation — hoje as 4 (Enrichment, Credit Validation, Stock Validation, Route Calculation) são todas hard-blocking, mesmo as que não têm ação compensatória.
_Avoid_: required step, mandatory step

**Compensating Action**:
Ação executada por uma Processing Step compensatória (hoje: Credit Validation, Stock Validation) para desfazer seu próprio efeito colateral quando o Order é cancelado. Executada pelo mesmo MS dono do efeito colateral, sobre sua própria base — nunca por outro serviço. Ver [[0002-compensating-flow-for-order-processing]] (ADR raiz).
_Avoid_: rollback (termo genérico demais fora do contexto de transação de banco), undo

**Order Item**:
Uma linha de produto dentro de um Order — SKU, quantidade, preço unitário, dado de produto. O Order é o aggregate root; Order Item nunca existe fora de um Order, e a concorrência (`version`) é do Order inteiro, não por item — ver [[0001-optimistic-locking-for-order-writes]].
_Avoid_: line item (isolado, sem o resto do vocabulário em inglês do contexto), produto do pedido

**Snapshot**:
Cópia denormalizada de dado de outro bounded context (Customer, Product), gravada no Order/Order Item no momento da Data Enrichment. Não é fonte de verdade — [Customers](../customers/CONTEXT.md)/Products continuam donos do dado real; o snapshot só existe pra o Order não precisar consultar outro MS depois de enriquecido. Carrega dado de negócio do pedido (nome, documento, endereço de entrega, descrição de produto) — nunca dado de contato, que precisa estar atualizado no momento de uso e por isso é consultado direto no dono.
_Avoid_: cache — um Snapshot é permanente e nunca invalida, ao contrário do Customer Registry Cache, que é a única coisa neste contexto que merece esse nome

**Import Batch**:
Um upload de planilha de import em massa — existe como entidade própria (`PENDING`/`PROCESSING`/`COMPLETED`/`COMPLETED_WITH_ERRORS`) antes de qualquer Order ser criado a partir dele. Planilha é denormalizada: cabeçalho do pedido (`source_system_code`, `source_order_number`, `customer_id`, `order_created_at`) repetido em toda linha de item. Ver [[0004-async-bulk-import-with-batch-status]].
_Avoid_: import job (genérico, "batch" já é o termo canônico aqui), upload (é só o gatilho, não a entidade)

**Import Fingerprint**:
Hash do conteúdo do arquivo de import, calculado no upload. Arquivo já importado é recusado na hora, sem criar Import Batch. Camada distinta do Source Order Number: o fingerprint pega o reenvio do mesmo arquivo, o Source Order Number pega o pedido repetido entre arquivos diferentes.
_Avoid_: checksum, hash do arquivo (sem qualificar o papel), deduplicação de upload

**Customer Registry Cache**:
Cache em memória do MS Order que guarda quais `customer_id` existem no [Customers](../customers/CONTEXT.md), pra o import não consultar o mesmo cliente a cada linha. Guarda existência e nada mais — nunca dado de cliente, nunca status (que não existe do lado de lá). Só resultado positivo entra; miss e negativo sempre vão ao dono. Não confundir com Snapshot: este é volátil e é detalhe de eficiência, o Snapshot é permanente e é dado de negócio.
_Avoid_: cache de cliente (ambíguo com Snapshot), customer store, réplica local

**Rejected Order Row**:
Linha da planilha recusada na validação do import, registrada como erro do Import Batch enquanto as demais linhas seguem — `customer_id` inexistente é a causa nova. O Import Batch fecha em `COMPLETED_WITH_ERRORS`. Indisponibilidade do Customers nunca produz Rejected Order Row: nesse caso o Order é criado e a validação real acontece na Data Enrichment, pra que um MS fora do ar não vire perda de venda.
_Avoid_: linha inválida (genérico), erro de import, pedido rejeitado (ambíguo com falha de Processing Step)

**Source Order Number**:
Chave natural do Order no sistema de origem (`source_system_code` + `source_order_number`) — não é o `id` interno do Order, é o identificador que o sistema que gerou o pedido usa. Par único, usado pra detectar duplicata entre lotes de import.
_Avoid_: order number (sem qualificar "source", ambíguo com o `id` interno)
