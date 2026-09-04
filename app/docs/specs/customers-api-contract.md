# Integration: MS Customers (REST síncrono) ↔ MS Notifications

**Status:** Draft
**Author:** george@bonespirito.com (via Claude Code, sessão de grilling)
**Date:** 2026-09-01 (revisado 2026-09-03 — MS Order deixou de ser consumidor)

## Purpose

MS Customers é fonte de verdade do cadastro de clientes ([`app/customers/CONTEXT.md`](../../customers/CONTEXT.md)). Ele participa do pipeline de pedidos de forma **assíncrona**: responde à task de Customer Enrichment por worker-polling e publica o resultado em Kafka (contrato em [`processing-steps-kafka-integration.md`](./processing-steps-kafka-integration.md)).

Existe uma única exceção síncrona em todo o sistema — o **Customers Lookup**, usado por um consumidor só:

| Consumidor | Quando | Por quê |
|---|---|---|
| MS Notifications | ao processar mudança de status vinda do CDC | obter o WhatsApp Phone atual — ele não viaja no Snapshot de propósito ([ADR notifications/0003](../../notifications/docs/adr/0003-lookup-contact-at-send-time.md)) |

**MS Order não é consumidor.** Uma versão anterior deste documento previa validação de `customer_id` em lote no import; foi revertida — o Order não consulta nenhum contexto de forma síncrona, e dado de cadastro faltando é resolvido pela Processing Step ([ADR order/0011](../../order/docs/adr/0011-async-only-enrichment-await-master-data.md)).

## Operation

### Busca de contato — consumidor: MS Notifications

Recebe um `customer_id` e devolve o WhatsApp Phone atual. Uma chamada por notificação a enviar; volume é baixo (uma por mudança de status de Order), não justifica cache.

- Cliente inexistente e cliente sem WhatsApp Phone são respostas distintas: a primeira é Customer Not Found, a segunda é um cliente que não pode ser notificado por este canal.
- Não devolve status do cliente: o cadastro não tem estado ([`Customer Status`](../../customers/CONTEXT.md) é registrado como termo explicitamente inexistente).

## Failure semantics

| Situação | MS Notifications |
|---|---|
| `customer_id` inexistente | Customer Not Found; a notificação não é enviável, vai ao DLT sem retry |
| Customers indisponível (timeout, 5xx) | retry curto, depois DLT do Notifications |

A classificação transitório/permanente segue o mesmo critério de [ADR order/0007](../../order/docs/adr/0007-dlt-exception-classifier.md): default é permanente, timeout e erro de infraestrutura são transitórios.

## Open

- Forma exata do endpoint (path, verbo, payload, versionamento) não desenhada — implementação dos MS de dado é incremental (PRD, seção 1). Este documento fixa a operação, o consumidor e a semântica de falha; a forma vem na iteração que implementar Customers.
- Autenticação entre serviços dentro do cluster não decidida.
