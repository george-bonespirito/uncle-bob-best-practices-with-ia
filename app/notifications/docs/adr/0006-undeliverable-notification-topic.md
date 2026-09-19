# Tópico `notification.undeliverable`: schema próprio, sem PII, métrica própria

O DLT do Notifications existia como decisão e não tinha tópico. Três documentos afirmam que ele existe — `CONTEXT.md` (termo **Undeliverable Notification**), [ADR 0004](./0004-notifiable-statuses-and-idempotency.md) ("retry curto e, esgotado, DLT") e [ADR 0003](./0003-lookup-contact-at-send-time.md) (Customers fora do ar) — e a [ADR 0002](./0002-meta-cloud-api-for-whatsapp.md) já tinha sinalizado a lacuna textualmente. Nenhuma nomeava o tópico, nenhuma dizia o que vai dentro.

Virou urgente porque a infra foi declarar as 10 identidades e ACLs da fase 2 da ADR-024: a contagem de tópicos era 15 (7 resultado + 7 `.dlt` + CDC) e faltava este.

## Decisões

**Nome: `notification.undeliverable`.** Não `order.*.result.dlt`: não é resultado de Processing Step, e o dono é este contexto, não o agregado Order. Não `notification.send.dlt`: `Undeliverable Notification` já é o termo do glossário, e o sufixo `.dlt` sugere companheiro de um `notification.result` que não existe.

**Schema próprio, não o `DltEnvelope` do Order.** Subject `com.bonespirito.notifications.dlt.UndeliverableNotification`, com `orderId`, `newStatus`, `templateUsed`, `failureReason`, `failedAt`, `attempt`.

Reusar o envelope da [ADR order/0020](../../../order/docs/adr/0020-dlt-envelope-schema-and-apicurio-ci-check.md) foi rejeitado por dois campos que mentiriam: `resultId` não existe aqui, e a **Notification Key** é `(order_id, novo_status)` — sem `newStatus` de primeira classe, quem resolve à mão não sabe *qual transição* não chegou ao cliente sem desserializar a linha de CDC. Um envelope compartilhado em que 2 de 7 campos não se aplicam é pior que dois schemas.

Sem `payloadOriginal`. No DLT do Order ele existe porque o problema *é* a mensagem malformada; aqui a linha de CDC bruta não ajuda a reenviar um WhatsApp.

**O telefone não entra no tópico.** Nem o número, nem qualquer PII. A regra de PII do padrão de observabilidade ([ADR raiz 0010](../../../docs/adr/0010-observability-standard.md)) já bastaria, e aqui o motivo é mais forte: WhatsApp é canal externo. Um tópico Kafka é PII em disco, replicada, sem retenção definida.

Consequência aceita: o operador que reenvia busca o contato no Customers pelo `orderId`. É o que o envio já faz ([ADR 0003](./0003-lookup-contact-at-send-time.md)), e pelo mesmo motivo — **telefone congelado no DLT reenviaria para o número antigo**, transformando uma falha de entrega em mensagem para a pessoa errada.

**1 partição, RF 1.** Igual aos outros `.dlt` e ao CDC. Volume baixo, resolução manual, e ordem entre falhas de envio não significa nada.

**Ninguém consome, automatizado.** Igual à ADR raiz 0005: resolução é manual.

**Métrica própria, não a profundidade agregada dos `.dlt`.** O padrão de observabilidade adotou o critério de que métrica sem ação nomeada não existe, e as ações aqui divergem: profundidade de `.dlt` do Order significa mensagem malformada a investigar; profundidade deste significa **cliente que não foi avisado** — fila de operação com prazo, não investigação. Somar as duas numa métrica só esconderia a que tem prazo.

**Entra no CI de compatibilidade, com `RecordNameStrategy` e `BACKWARD`.** A ADR order/0020 escopou o check *exclusivamente* aos 7 `.dlt` do Order; este é o segundo caso do mesmo tipo — schema autoral nosso, versionado no repo, com `.avsc` local. O motivo de excluir CDC e tópicos de resultado lá (schema inferido em runtime, ou autorado por serviço que não existe) não se aplica aqui. `BACKWARD` pelo mesmo raciocínio: o consumo é sempre humano e ad-hoc, então ferramenta de inspeção antiga tem de continuar lendo mensagem nova.

Arquivo: `app/notifications/src/main/resources/avro/undeliverable-notification.avsc` — **não criado ainda**, porque o módulo `:notifications` só tem a classe de aplicação. Entra com a implementação.

## Consequências

- A infra ganha um 15º tópico para declarar na fase 2 da ADR-024, com Write para `ms-notifications`. Até aqui ela ia dar a essa identidade só Read em `order.cdc.public.orders`.
- O `.avsc` e o step de CI só existem quando o módulo for implementado. Até lá esta ADR é contrato, não código.
- `notification.` é prefixo novo no cluster — o primeiro tópico que não começa com `order.`. Se a ACL da infra for por prefixo, e não literal por tópico, isso importa.

Status: accepted
