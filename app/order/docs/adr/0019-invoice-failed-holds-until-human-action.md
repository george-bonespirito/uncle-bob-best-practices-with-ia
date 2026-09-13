# Falha da Invoice Generation prende crédito e estoque até ação humana

Supersede a seção “Falha cancela e compensa” da [ADR order/0016](./0016-invoice-is-internal-and-terminal.md). As outras seções da 0016 (documento interno, numeração com buracos, Invoice emitida é terminal) continuam.

As 4 Processing Steps hard-blocking já tiveram sucesso quando o Order entra em `INVOICING`: crédito está reservado no MS Contabil e estoque está reservado no MS Stock. A 0016 decidiu que falha permanente da Invoice Generation cancela o Order e compensa os dois, para não travar recurso de terceiros por falha nossa.

Decidimos o contrário no caminho automático:

1. **Enquanto o retry automático ainda roda**, o Order permanece `INVOICING`. Falha da Invoice não cancela e não dispara compensação.
2. **Quando os retries esgotam**, o Order vai para `INVOICE_FAILED`. Crédito e estoque continuam reservados.
3. **Só ação humana tira o Order daí**, e há duas saídas: reprocessar a Invoice (volta a `INVOICING`, e daí a `COMPLETED` se emitir) ou Cancellation (dispara Compensating Action e termina em `CANCELLED`).

## Por que não compensar automaticamente

Compensar na hora transformaria falha transitória de emissão em perda de venda: o pedido some, o estoque volta, o cliente não leva o produto. O Order já passou Credit Validation e Stock Validation — é um pedido vendável esperando um documento interno. Prender o recurso e esperar correção da Invoice é o mesmo tipo de escolha que `AWAITING_MASTER_DATA`, com uma diferença: aqui há custo (estoque e crédito presos), então a espera **não** é indefinida — o retry automático tem teto, e depois disso a fila de trabalho é `INVOICE_FAILED`, consultável.

A 0016 recusou retry indefinido em `INVOICING`. Esta ADR concorda com o teto; discorda do cancelamento automático ao atingi-lo.

## Por que um status próprio, e não ficar em `INVOICING`

`INVOICING` significa retry ainda ativo. `INVOICE_FAILED` significa teto atingido, intervenção humana obrigatória. Misturar os dois faria o status mentir — parecer “ainda emitindo” quando o Orkes já desistiu.

## Interação com a liberação parcial da ADR 0018

A Invoice Generation, **antes** de emitir, libera o estoque dos itens que caíram na reverificação de Product Status e converte o Credit Hold pelo valor faturado ([ADR 0018](0018-order-shrinkage-at-invoice.md)). A ordem importa quando a emissão falha depois disso.

Estado de um Order em `INVOICE_FAILED`, sendo preciso: **estoque parcialmente liberado, Credit Hold intacto.** A conversão acontece *na* emissão, então emissão que não aconteceu não converteu nada — e `release-credit-hold` continua funcionando sobre um hold que ainda é hold.

Duas consequências para quem implementar o reprocessamento:

- **O conjunto de itens é recalculado, não reaproveitado.** Entre a tentativa que falhou e o reprocessamento, outro produto pode ter sido desativado.
- **A liberação não pode acontecer duas vezes.** A idempotência da 0018 cobre — liberar item já liberado é no-op —, mas só porque ela existe. Sem ela, o reprocessamento devolveria estoque que já tinha voltado.

## `INVOICE_FAILED` não notifica o cliente

A [ADR notifications/0004](../../../notifications/docs/adr/0004-notifiable-statuses-and-idempotency.md) define Notifiable Status como `PROCESSING`, `CANCELLED` e `COMPLETED` — escrita quando o Order tinha oito status. Com o nono, a regra precisa dizer de que lado ele cai.

`INVOICE_FAILED` **não notifica**, pelo mesmo critério dos outros internos: o cliente não pode agir sobre um retry de nota que esgotou, e avisá-lo expõe falha interna como se fosse informação.

Uma consequência que vale estar escrita: **Cancellation humana termina em `CANCELLED`, que é notificável.** O cliente recebe "pedido cancelado" sem distinção entre cancelamento por falha e desistência nossa. É aceitável — o template é genérico por desenho —, mas é decisão, não consequência.

## Alternativas rejeitadas

- **Cancelar e compensar ao esgotar o retry** (a 0016). Rejeitada porque descarta um pedido já reservado por um problema de emissão, não de crédito ou estoque.
- **Retry infinito em `INVOICING`.** Rejeitada — a 0016 acertou neste ponto; `COMPENSATION_FAILED` já estabeleceu que estado terminal existe precisamente para não haver retry silencioso sem teto.
- **Reusar `COMPENSATION_FAILED`.** Rejeitada porque ali falhou a compensação, não a Invoice — e as saídas são outras (não há “reprocessar a Invoice”).

Custo aceito: um Order em `INVOICE_FAILED` ocupa estoque e crédito até alguém reprocessar ou cancelar. Sem Cancellation humana, o recurso não se solta sozinho. É o custo que a 0016 recusou; fica explícito.

Status: accepted

## Revisão (2026-09-12) — Cancellation também existe em `PROCESSING`

A decisão desta ADR (falha da Invoice não compensa sozinha; `INVOICE_FAILED` prende recurso até ação humana) não muda. O que se amplia é o termo **Cancellation**: não é exclusivo de `INVOICE_FAILED`. Um Order em `PROCESSING` — inclusive com crédito e estoque já reservados — também pode ser desistido por ação humana, e aí a Compensating Action segue o mesmo caminho (`COMPENSATING` → `CANCELLED`).

## Revisão (2026-09-12) — Cancellation nos demais estados pré-COMPLETED

Cancellation também vale em `PENDING`, `AWAITING_MASTER_DATA` e `INVOICING`. Sem reserva (os dois primeiros) vai a `CANCELLED` direto. Com reserva (`INVOICING`, e `PROCESSING` depois de Credit/Stock) compensa. `COMPLETED` não cancela.
