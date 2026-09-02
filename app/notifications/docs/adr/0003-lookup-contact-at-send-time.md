# Buscar o WhatsApp Phone no MS Customers na hora do envio, em vez de recebê-lo pelo CDC

O `status_change_notification.feature` sempre disse "cliente com WhatsApp cadastrado", mas o número não existia em lugar nenhum do sistema: MS Notifications lê só o tópico CDC da tabela `orders` ([ADR notifications/0001](./0001-consume-order-cdc-topic.md)), e `orders` não tinha coluna de contato. O gap passou despercebido porque nenhum documento era dono da pergunta "de onde vem o número".

Decidimos que **MS Notifications consulta o MS Customers no momento de enviar a mensagem**, usando o `customer_id` que vem no evento CDC (contrato em [`customers-api-contract.md`](../../../docs/specs/customers-api-contract.md)).

**Alternativa rejeitada: levar o número no `customer_snapshot`.** Era a opção barata — o Snapshot já viaja pelo CDC, Notifications continuaria sem dependência síncrona de ninguém, e o caminho já existia. Rejeitada porque o `Snapshot` é permanente por definição e nunca invalida: um número copiado no momento da Data Enrichment nunca mais seria atualizado, e cliente que troca de telefone deixaria de receber notificação do próprio pedido, em silêncio, sem erro em lugar nenhum. Dado de contato é a categoria de dado onde estar desatualizado é indistinguível de estar quebrado.

A regra que fica: **o Snapshot carrega dado de negócio do pedido — nome, documento, endereço de entrega, descrição de produto — e nunca dado de contato.** Dado de contato precisa estar correto no instante do uso, então é consultado no dono.

**Consequências:**

- Notifications passa a depender do MS Customers em runtime. Customers fora do ar significa notificação não enviada: retry curto e depois DLT, seguindo o padrão que a [ADR notifications/0002](./0002-meta-cloud-api-for-whatsapp.md) já antecipava como provável. Antes desta decisão, Notifications só dependia do Kafka.
- Uma chamada síncrona por notificação enviada. Volume é baixo (uma por mudança de status de Order), não justifica cache.
- Cliente sem WhatsApp Phone cadastrado e cliente inexistente são respostas distintas do Customers, e Notifications precisa tratá-las como tal — a primeira é um cliente que não pode ser notificado por este canal, a segunda é inconsistência de dado.
- A validação de `customer_id` no import ([ADR order/0009](../../../order/docs/adr/0009-validate-customer-at-import-degrade-on-outage.md)) é o que torna esta decisão viável: sem ela, o `customer_id` só seria confiável depois da Data Enrichment, e a primeira notificação (`PENDING` → `PROCESSING`) dispara antes disso.

Status: accepted
