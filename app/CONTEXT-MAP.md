# Context Map

Sistema de referência de supply chain: um Order é criado via importação em massa (planilha) e passa por Processing Steps orquestradas pelo Orkes, cada uma delegada a um microsserviço dono do dado correspondente.

## Contexts

- [Order](./order/CONTEXT.md) — dono do Pedido de compra e seu status; único writer do agregado, recebe o resultado de todas as Processing Steps
- [Customers](./customers/CONTEXT.md) — cadastro de clientes; fonte de verdade de nome, documento, endereços de entrega e WhatsApp do comprador
- [Products](./products/CONTEXT.md) — cadastro de produtos; fonte de verdade de nome, categoria, atributos físicos e situação (`ACTIVE`/`INACTIVE`)
- [Pricing](./pricing/CONTEXT.md) — valor unitário por SKU; catálogo único, sem preço por cliente nem desconto
- [Stock](./stock/CONTEXT.md) — quantidade disponível por SKU e as reservas sobre ela; estoque único, sem localização
- [Contabil](./contabil/CONTEXT.md) — limite de crédito por cliente e os holds sobre ele; sem pagamento, sem score
- Invoice — nota fiscal _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Route — envio e tracking do pedido _(ainda sem CONTEXT.md — linguagem não resolvida)_
- Notifications — envio de notificação ao cliente a cada mudança de status do pedido (canal WhatsApp hoje) _(ainda sem CONTEXT.md — linguagem não resolvida)_

## Relationships

- **Orkes ↔ Customers, Products, Pricing, Stock, Contabil, Invoice, Route**: worker-polling — cada MS roda um worker que faz polling na fila de tasks do Orkes, executa a Processing Step, reporta conclusão via API do Conductor. Nenhum MS expõe endpoint HTTP alcançável pelo Orkes — ver [ADR raiz 0001](./docs/adr/0001-worker-polling-for-processing-steps.md)
- **Customers, Products, Pricing, Stock, Contabil, Invoice, Route → Order (via Kafka)**: cada MS publica o resultado da sua Processing Step num único tópico Kafka, não particionado por `order_id` (de propósito, pra preservar concorrência real entre instâncias de MS Order). MS Order grava em staging (`order_update_event`) e aplica via pool de workers com `SKIP LOCKED` + optimistic locking — ver [order ADR 0003](./order/docs/adr/0003-kafka-staging-skip-locked-for-processing-results.md)
- **Notifications ← Order (via Kafka)**: consome o tópico CDC do Debezium `order.cdc.public.orders` (ADR 0002), filtrando eventos onde `before.status != after.status`; acoplado ao schema da tabela `orders` — ver [notifications ADR 0001](./notifications/docs/adr/0001-consume-order-cdc-topic.md)
- **Route → Products, Invoice Generation → Products (síncrono)**: Products Lookup. O Route busca Physical Attributes pra calcular carga; a Invoice Generation reconfere o Product Status imediatamente antes de faturar, porque até faturar o pedido ainda pode mudar — ver [order ADR 0012](./order/docs/adr/0012-partial-fulfillment-for-inactive-products.md). É a segunda consulta síncrona do sistema, ao lado do Customers Lookup.
- **Order → nenhum MS de dado, de forma síncrona**: o import não consulta Customers nem Products pra validar `customer_id`/`sku`. Todo enriquecimento é assíncrono (worker-polling + Kafka); cadastro faltando trava a task no Orkes até o dado existir, em vez de recusar a linha — ver [order ADR 0011](./order/docs/adr/0011-async-only-enrichment-await-master-data.md)
- **Notifications → Customers (REST síncrono, no envio)**: o WhatsApp Phone não viaja no CDC — Notifications busca o número no [Customers](./customers/CONTEXT.md) ao processar a mudança de status, pra que troca de número valha imediatamente. Customers indisponível: retry curto e DLT do Notifications. Mesmo contrato acima
- **Orkes → Contabil, Stock (compensação)**: só essas duas têm ação compensatória (efeito colateral real); disparada via worker-polling quando uma Processing Step hard-blocking falha permanentemente — ver [ADR raiz 0002](./docs/adr/0002-compensating-flow-for-order-processing.md)
