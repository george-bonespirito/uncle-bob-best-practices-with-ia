# Integration: MS Customers (REST síncrono) ↔ MS Order, MS Notifications

**Status:** Draft
**Author:** george@bonespirito.com (via Claude Code, sessão de grilling)
**Date:** 2026-09-01

## Purpose

MS Customers é fonte de verdade do cadastro de clientes ([`app/customers/CONTEXT.md`](../../customers/CONTEXT.md)). Além de responder a task de Customer Enrichment por worker-polling (contrato Kafka em [`processing-steps-kafka-integration.md`](./processing-steps-kafka-integration.md)), ele expõe consulta síncrona — o **Customers Lookup** — para dois consumidores em momentos diferentes do fluxo:

| Consumidor | Quando | Por quê |
|---|---|---|
| MS Order | no import da planilha, antes de criar qualquer Order | não iniciar workflow com `customer_id` que não existe |
| MS Notifications | ao processar mudança de status vinda do CDC | obter o WhatsApp Phone atual — ele não viaja no Snapshot de propósito |

Este é o único acoplamento síncrono entre bounded contexts do sistema. Todo o resto é Kafka.

## Operations

### 1. Existência em lote — consumidor: MS Order

Recebe uma lista de `customer_id` e devolve quais existem. Chamada uma vez por Import Batch, com os IDs distintos do arquivo (até ~50.000 linhas, mas o número de clientes distintos é muito menor — e o Customer Registry Cache do MS Order absorve repetição entre imports).

- Só existência. Não devolve nome, documento, endereço nem status — o cadastro não tem status ([`Customer Status`](../../customers/CONTEXT.md) é explicitamente inexistente).
- Resposta parcial é normal: IDs ausentes da resposta são os inexistentes.

### 2. Busca de contato — consumidor: MS Notifications

Recebe um `customer_id` e devolve o WhatsApp Phone atual. Uma chamada por notificação a enviar.

- Cliente inexistente e cliente sem WhatsApp Phone são respostas distintas: a primeira é Customer Not Found, a segunda é cliente que não pode ser notificado por este canal.

## Failure semantics

A regra que atravessa os dois consumidores: **"não existe" e "não consegui perguntar" nunca podem ser tratados como a mesma coisa.**

| Situação | MS Order (import) | MS Notifications (envio) |
|---|---|---|
| `customer_id` inexistente | Rejected Order Row — a linha vira erro do Import Batch, as demais seguem, batch fecha `COMPLETED_WITH_ERRORS` | Customer Not Found; notificação não é enviável, vai ao DLT sem retry |
| Customers indisponível (timeout, 5xx) | **não recusa nada** — Orders são criados e a validação real acontece na Customer Enrichment (hard-blocking, com retry próprio) | retry curto, depois DLT do Notifications |

O raciocínio do lado do Order é deliberado: recusar linha por indisponibilidade transforma um MS fora do ar em perda de venda. O custo aceito é um Order que queima uma volta de workflow antes de falhar em Customer Enrichment.

A classificação transitório/permanente segue o mesmo critério já estabelecido em [ADR order/0007](../../order/docs/adr/0007-dlt-exception-classifier.md): default é permanente, timeout e erro de infraestrutura são transitórios.

## Open

- Forma exata dos endpoints (paths, verbos, formato de payload, versionamento) não desenhada — implementação dos MS de dado é incremental (PRD, seção 1). Este documento fixa as operações, os consumidores e a semântica de falha; a forma vem na iteração que implementar Customers.
- Autenticação entre serviços dentro do cluster não decidida.
