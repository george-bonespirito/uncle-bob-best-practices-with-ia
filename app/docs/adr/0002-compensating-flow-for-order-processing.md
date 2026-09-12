# Fluxo compensatório (Saga) para falha nas Processing Steps

O Order passa por 4 Processing Steps hard-blocking em paralelo (Data Enrichment, Credit Validation, Stock Validation, Route Calculation) e, só depois de todas terem sucesso, por Invoice Generation, sequencial e por último. Duas dessas etapas têm efeito colateral real num serviço próprio (database-per-service): Credit Validation pode reservar/bloquear limite de crédito no MS Contabil; Stock Validation decrementa/reserva estoque no MS Stock. Enrichment e Route Calculation não têm efeito colateral (são leitura/cálculo). Invoice Generation nunca precisa de compensação, por desenho — só roda depois que as 4 anteriores já tiveram sucesso confirmado.

Decidimos:

1. **Ordem do workflow**: fork-join de Enrichment + Credit Validation + Stock Validation + Route Calculation; as 4 são hard-blocking (falha permanente de qualquer uma cancela o Order, mesmo as sem efeito colateral). Só com as 4 concluídas com sucesso o Orkes dispara Invoice Generation.
2. **Compensação**: se qualquer uma das 4 falhar permanentemente (após esgotar retry), o Orkes invoca a ação compensatória de cada etapa que **já tinha reportado sucesso** entre Credit Validation e Stock Validation (nenhuma, uma, ou as duas — depende do que já tinha terminado quando a falha foi detectada). A ação compensatória roda no mesmo MS dono do efeito colateral, sobre sua própria base — nunca via chamada cross-service direta a outro MS.
3. **Transporte da compensação**: mesmo mecanismo de worker-polling já decidido para o caminho feliz (ADR raiz 0001) — cada MS compensatório registra um worker adicional para o tipo de task de compensação (ex: `release-stock-reservation`, `release-credit-hold`).
4. **Status do Order**: `PENDING → PROCESSING → (COMPENSATING → CANCELLED) | (INVOICING → COMPLETED)`, com `COMPENSATION_FAILED` como estado terminal adicional — ver glossário completo em [`app/order/CONTEXT.md`](../../order/CONTEXT.md).
5. **Compensação que falha**: se uma ação compensatória ela mesma falhar (ex: MS Stock indisponível na hora de liberar a reserva), o Order vai para `COMPENSATION_FAILED` após esgotar um número limitado de retries — não fica em retry infinito silencioso. Esse estado exige intervenção manual; está fora de escopo deste projeto automatizar a resolução dele.

Alternativa rejeitada: permitir que Enrichment/Route Calculation sejam best-effort (não bloqueiam o Order mesmo falhando). Rejeitada porque geraria Orders com nota fiscal emitida sobre dado potencialmente incompleto — pior para o objetivo de aprendizado do que simplificar a modelagem de compensação.

Status: accepted

**Revisão (2026-09-03) — a ordem mudou, a decisão não.** As 5 Processing Steps passaram a executar em sequência por pedido ([ADR raiz 0008](./0008-sequential-processing-steps-per-order.md)): o fork-join das 4 descrito no item 1 não existe mais. O que muda é o item 2: o conjunto de etapas a compensar deixa de depender do timing ("o que já tinha terminado quando a falha foi detectada") e passa a ser determinístico — compensam-se as etapas anteriores à que falhou na fila. Tudo o mais desta ADR continua valendo: quais etapas têm efeito colateral, quem executa a compensação, o transporte por worker-polling e o tratamento de `COMPENSATION_FAILED`. O ciclo de status do item 4 ganha `AWAITING_MASTER_DATA` ([ADR order/0011](../../order/docs/adr/0011-async-only-enrichment-await-master-data.md)), possível só durante a Data Enrichment.

---

## Revisão — 2026-09-12

Esta ADR descreve a Stock Validation como quem "decrementa/reserva" estoque. **Reservar não é decrementar**, e a diferença passou a ter nome com o [glossário do Stock](../../stock/CONTEXT.md): `Stock Balance` é a quantidade física e não muda na reserva; o que muda é a `Available Quantity`, que é derivada.

A decisão desta ADR não muda — a compensação continua liberando o que foi reservado, por `stockReservationId`, de forma idempotente. O que muda é o modelo mental: implementar "decrementar" como `balance = balance - qty` apagaria a distinção entre físico e disponível e tornaria a liberação indistinguível de uma entrada de mercadoria.

Ver também [ADR order/0014](../../order/docs/adr/0014-out-of-stock-cancels-order.md), que define o escopo da reserva e por que ela não expira.

