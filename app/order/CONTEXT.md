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
