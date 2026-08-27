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
