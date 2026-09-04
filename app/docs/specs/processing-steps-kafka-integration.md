# Integration: MS de dado (Customers/Products/Pricing/Contabil/Stock/Route/Invoice) ↔ MS Order (via Kafka)

**Status:** Draft
**Author:** george@bonespirito.com (via Claude Code, sessão de grilling)
**Date:** 2026-08-27

## Purpose

Cada um dos 7 MS de dado, ao concluir sua task no workflow do Orkes (via worker-polling, ADR raiz 0001), publica o resultado num tópico Kafka dedicado. MS Order consome, grava em staging e aplica ao Order (ADR order/0003), e o Orkes usa o resultado (sucesso/falha) para decidir avanço ou compensação do workflow (ADR raiz 0002).

## Contract

- **Protocol:** Kafka, Avro + Apicurio Registry (ADR raiz 0003)
- **Direction:** MS de dado → MS Order, unidirecional. MS Order nunca publica nesses tópicos.
- **Topics** (um por task do Conductor — ADR raiz 0004; Data Enrichment é sub-workflow de 3 tasks, ADR raiz 0001):

| Tópico | Producer | Payload (Avro, além do envelope comum) |
|---|---|---|
| `order.customer-enrichment.result` | MS Customers | `customerSnapshot` (`name` legal, `document`, endereço de entrega padrão) — sem contato: WhatsApp Phone é consultado em runtime, ver [`customers-api-contract.md`](./customers-api-contract.md) |
| `order.product-enrichment.result` | MS Products | `productSnapshots[]` (sku, descrição, categoria por item do pedido) |
| `order.pricing.result` | MS Pricing | `unitPrices[]` (sku, valor unitário, moeda) |
| `order.credit-validation.result` | MS Contabil | `creditStatus` (APPROVED/REJECTED), `creditHoldId` (nullable, presente só se APPROVED) |
| `order.stock-validation.result` | MS Stock | `stockReservationId` (nullable, presente só se sucesso), `reservedItems[]` |
| `order.route-calculation.result` | MS Route | `routeId`, `trackingCode`, `estimatedDelivery` |
| `order.invoice-generation.result` | MS Invoice | `invoiceNumber`, `invoiceIssuedAt` |

- **Envelope comum** (todo tópico acima, além do `payload` específico da linha):

| Campo | Tipo | Descrição |
|---|---|---|
| `orderId` | string (UUID) | correlação com o Order |
| `resultId` | string (UUID) | identificador único da mensagem — chave de idempotência na aplicação ao Order |
| `workflowTaskId` | string | ID da task instance no Conductor — rastreabilidade |
| `attempt` | int | número da tentativa (1-based) que produziu este resultado |
| `outcome` | enum: `SUCCESS`, `FAILURE` | |
| `occurredAt` | long (epoch millis) | quando o MS terminou de processar |
| `failureReason` | string, nullable | populado só se `outcome=FAILURE` |
| `payload` | record | específico por tópico, null se `outcome=FAILURE` |

## Sequence diagram

Ver diagrama de sequência do fluxo compensatório em `app/docs/specs/order-processing-prd.md` (seção 4) — mesmo mecanismo, este documento detalha o contrato de mensagem que ali é só "publica resultado".

## Resilience

- **Timeout/retry do worker-polling (task do Conductor):** 3 tentativas, backoff exponencial (1s / 5s / 15s). Esgotadas as tentativas, a task é marcada como falha permanente — dispara compensação se a Processing Step for hard-blocking (ADR raiz 0002).
- **Idempotência de aplicação ao Order:** `resultId` é a chave — MS Order (ou o worker `SKIP LOCKED`) descarta silenciosamente uma mensagem com `resultId` já aplicado, sem erro.
- **Idempotência de compensação:** `creditHoldId`/`stockReservationId` são as chaves — a ação compensatória (`release-credit-hold`, `release-stock-reservation`) recebe o ID da reserva/hold a liberar; liberar um ID já liberado é no-op no MS dono (Contabil/Stock), não erro. Resolve a lacuna de idempotência apontada como risco no PRD.
- **Dead letter:** um tópico `.dlt` por tópico de resultado (ex: `order.stock-validation.result.dlt`) — ver ADR raiz 0005. Classificador transitória/permanente com default para permanente (ADR order/0007): só `TransientDataAccessException` e timeout do Apicurio contam como transitório (retry curto, 3x, 200ms/500ms/1s); tudo mais — deserialização Avro, `orderId` inexistente, exceção não listada — vai direto ao DLT. Resolução manual, fora de escopo automatizar.

## Testing

- **Contract test:** schema Avro de cada tópico versionado no Apicurio Registry; mecanismo de validação em CI (ex: schema compatibility check antes de publish) não desenhado ainda.
- **Integration test:** fora de escopo desta rodada — como testar a Saga ponta a ponta continua em aberto (gray area #9 do mapa de partes cinzentas).
