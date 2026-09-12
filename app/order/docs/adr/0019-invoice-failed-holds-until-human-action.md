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
