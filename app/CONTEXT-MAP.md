# Context Map

Sistema de referência de supply chain: um Order é criado via importação em massa (planilha) e passa por Processing Steps orquestradas pelo Orkes, cada uma delegada a um microsserviço dono do dado correspondente.

## Contexts

- [Order](./order/CONTEXT.md) — dono do Pedido de compra e seu status; único writer do agregado, recebe o resultado de todas as Processing Steps
- Customers — cadastro de clientes _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Products — cadastro de produtos _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Pricing — valor unitário por SKU _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Stock — estoque por produto/SKU _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Contabil — dado de crédito do cliente _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Invoice — nota fiscal _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Route — envio e tracking do pedido _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Notifications — envio de notificação ao cliente a cada mudança de status do pedido (canal WhatsApp hoje) _(ainda sem CONTEXT.md — linguagem não resolvida)_

## Relationships

- **Orkes ↔ Customers, Products, Pricing, Stock, Contabil, Invoice, Route**: worker-polling — cada MS roda um worker que faz polling na fila de tasks do Orkes, executa a Processing Step, reporta conclusão via API do Conductor. Nenhum MS expõe endpoint HTTP alcançável pelo Orkes — ver [ADR raiz 0001](./docs/adr/0001-worker-polling-for-processing-steps.md)
- **Customers, Products, Pricing, Stock, Contabil, Invoice, Route → Order (via Kafka)**: cada MS publica o resultado da sua Processing Step num único tópico Kafka, não particionado por `order_id` (de propósito, pra preservar concorrência real entre instâncias de MS Order). MS Order grava em staging (`order_update_event`) e aplica via pool de workers com `SKIP LOCKED` + optimistic locking — ver [order ADR 0003](./order/docs/adr/0003-kafka-staging-skip-locked-for-processing-results.md)
- **Notifications ← Order (via Kafka)**: consome o mesmo tópico CDC do Debezium (ADR 0002), filtrando eventos onde `before.status != after.status`; acoplado ao schema da tabela `orders` — ver [notifications ADR 0001](./notifications/docs/adr/0001-consume-order-cdc-topic.md)
- **Orkes → Contabil, Stock (compensação)**: só essas duas têm ação compensatória (efeito colateral real); disparada via worker-polling quando uma Processing Step hard-blocking falha permanentemente — ver [ADR raiz 0002](./docs/adr/0002-compensating-flow-for-order-processing.md)
