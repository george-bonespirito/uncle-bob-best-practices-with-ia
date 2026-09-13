# Só três status notificam, e cada transição gera uma mensagem só

O `status_change_notification.feature` dizia "a cada mudança de status". Com o ciclo de vida do Order fechado — oito status —, isso deixou de ser suficiente: três são internos e um é lacuna nossa.

## Notificam: `PROCESSING`, `CANCELLED`, `COMPLETED`

São os três que o cliente entende e sobre os quais ele pode formar expectativa: o pedido começou a andar, foi cancelado, ou terminou.

Ficam de fora:

- **`PENDING`** — estado inicial, não é transição. Já coberto por cenário.
- **`INVOICING`**, **`COMPENSATING`**, **`COMPENSATION_FAILED`** — internos. Avisar o cliente de que uma compensação falhou não lhe permite agir e expõe falha interna como se fosse informação.
- **`AWAITING_MASTER_DATA`** — lacuna de cadastro **nossa**. O cliente não pode cadastrar o produto que faltou; avisá-lo transferiria a ele um problema que é de operação.

A regra some no código se não estiver nomeada, então o glossário define `Notifiable Status` como termo.

## Uma mensagem por transição

O CDC é at-least-once: o mesmo evento pode chegar duas vezes, por reprocessamento ou rebalance. Sem idempotência, o cliente recebe dois WhatsApps.

A chave é `(order_id, novo_status)`. Funciona porque cada transição acontece uma vez na vida do pedido — não há volta a um status já visitado no fluxo desenhado.

Dedupe por offset **não** serve: o offset muda quando o tópico é reprocessado, que é exatamente o cenário que a chave precisa cobrir.

Mensagem duplicada é dano diferente de processamento duplicado: não é reversível, chega ao cliente e fica no aparelho dele.

## Falha não afeta o Order

Retry curto e, esgotado, DLT. O Order segue.

Notificação **não faz parte da Saga**: não há ação compensatória para uma mensagem enviada, e não haveria sentido em cancelar um pedido porque o WhatsApp caiu. Isso também é o motivo de este contexto consumir CDC em vez de participar do workflow do Orkes ([ADR notifications/0001](0001-consume-order-cdc-topic.md)).

DLT em vez de descarte silencioso: cliente não avisado é problema de operação, e problema que ninguém vê não é resolvido.

## Template carrega número do pedido e status, nada mais

A Meta exige template pré-aprovado, o que já limita o conteúdo. A decisão é sobre o que **colocamos** no que é permitido.

Nada de itens, valores ou dados do cliente. Isso é a mesma regra de PII do [padrão de observabilidade](../../../docs/specs/observability-standard.md), com razão mais forte: WhatsApp é canal externo e o histórico fica no aparelho de quem recebe, fora de qualquer retenção que controlemos.

O cliente já sabe o que pediu; a mensagem existe para dizer em que pé está.

Status: accepted

---

## Revisão — 2026-09-13

O Order ganhou um nono status, `INVOICE_FAILED` ([ADR order/0019](../../../order/docs/adr/0019-invoice-failed-holds-until-human-action.md)). Esta ADR foi escrita com oito, então a lista de Notifiable Status precisava dizer de que lado ele cai.

**Não notifica**, pelo mesmo critério dos demais internos: o cliente não pode agir sobre um retry de nota esgotado, e avisá-lo expõe falha interna como informação.

A decisão desta ADR não muda. O que este caso mostra é que a lista de Notifiable Status **não é auto-evidente**: cada status novo do Order exige dizer aqui de que lado ele cai, senão quem implementar decide sozinho.

