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

- **Order ← Customers, Products**: Orkes chama essas APIs durante a Processing Step de Data Enrichment; resultado é reportado de volta ao Order
- **Order ← Pricing**: consumido durante Data Enrichment (ou etapa de precificação própria — ainda não confirmado)
- **Order ← Stock**: Orkes chama durante Stock Validation
- **Order ← Contabil**: Orkes chama durante Credit Validation
- **Order ← Invoice**: Orkes chama durante Invoice Generation
- **Order ← Route**: Orkes chama durante Route Calculation (envio/tracking)
- **Notifications ← Order (via Kafka)**: consome o mesmo tópico CDC do Debezium (ADR 0002), filtrando eventos onde `before.status != after.status`; acoplado ao schema da tabela `orders` — ver [notifications ADR 0001](./notifications/docs/adr/0001-consume-order-cdc-topic.md)
