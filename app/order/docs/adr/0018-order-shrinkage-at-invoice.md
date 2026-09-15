# Pedido que encolhe no faturamento: liberar o excedente em vez de deixá-lo preso

Três ADRs aceitaram, separadamente, que efeito colateral fica dimensionado antes de o pedido encolher: [0012](0012-partial-fulfillment-for-inactive-products.md) (compensação parcial não desenhada), [0015](0015-credit-hold-lifecycle.md) (hold maior que a fatura) e [0017](0017-route-is-a-plan-not-a-delivery.md) (rota superdimensionada). A 0017 notou que eram o mesmo fenômeno. Esta ADR resolve o que dele é resolvível.

O gatilho é único: a Invoice Generation reconfere o Product Status e um item pode virar `NOT_FULFILLED` **depois** de crédito e estoque já estarem comprometidos.

## Estoque: liberação parcial, e isso muda o contrato da task

O problema não é desperdício, é **vazamento**: a Stock Reservation cobre o item que caiu, e `release-stock-reservation` hoje libera a reserva inteira, por `stockReservationId`. Não existe liberar parte. Sem mudança, aquelas unidades saem da `Available Quantity` e nada mais aponta para elas — aparece quando o inventário não bate, meses depois.

Decidimos que **a task compensatória passa a aceitar uma lista de itens**, e a Invoice Generation libera só os que caíram, antes de emitir. A reserva sobrevive para o restante, com o mesmo id.

Rejeitada a alternativa de liberar tudo e re-reservar o conjunto final: abriria uma janela em que o estoque fica livre e outro pedido o toma — perderíamos uma venda já aprovada, por um conserto.

Isto **muda o contrato** de `release-stock-reservation`, que a [ADR raiz 0002](../../../docs/adr/0002-compensating-flow-for-order-processing.md) define. A idempotência continua valendo e ganha escopo: liberar um item já liberado é no-op, como liberar uma reserva já liberada.

## Crédito: converter o valor faturado e liberar a diferença

Mesma forma. O hold cobre 500, a fatura sai 400: converte-se 400 em dívida e liberam-se 100.

- Converter os 500 cobraria crédito por algo que não foi vendido.
- Converter 400 e deixar 100 em hold criaria hold órfão de um pedido já terminal — exatamente o estado que a [ADR 0015](0015-credit-hold-lifecycle.md) recusou ao decidir que hold não sobrevive ao fim do pedido.

A Hold Conversion passa a receber **valor**, não "o hold inteiro".

## Rota: continua sem recalcular

Aqui não há vazamento. Carga superdimensionada cabe no veículo e não prende recurso de terceiros — ninguém fica sem entrega porque a nossa rota foi dimensionada para mais peso.

Recalcular acrescentaria uma quarta responsabilidade à Invoice Generation, que já reconfere status, libera estoque e converte hold. A assimetria com estoque e crédito é deliberada: **liberamos o que outro pedido poderia usar; não refazemos o que não prende nada.**

## Cliente é avisado, sem detalhe de item

Item não atendido não muda status do Order, e o Notifications dispara por transição de status ([ADR notifications/0004](../../../notifications/docs/adr/0004-notifiable-statuses-and-idempotency.md)).

Decidimos um **template variante do `COMPLETED`**: "pedido concluído, com itens não atendidos", sem listar quais. Notificação nova por item contrariaria a regra de disparo por transição e traria detalhe de item para o WhatsApp, contra a decisão de conteúdo mínimo.

Não avisar deixaria o cliente descobrir sozinho que faltou coisa.

## Pedido inteiro sem itens: a regra existente basta

Quando **todos** os itens caem, o caminho até `CANCELLED` depende de já haver efeito colateral aplicado — que é a regra da ADR raiz 0002, não uma regra nova:

- Na **Product Enrichment**: vai direto a `CANCELLED`. Crédito e estoque ainda não existem.
- Na **Invoice Generation**: passa por `COMPENSATING`. Os dois existem e precisam ser revertidos.

O que faltava não era decisão, era alguém percorrer o caso.

## O que continua aberto

`COMPENSATION_FAILED` na liberação parcial: se a liberação dos itens que caíram falhar, o pedido não pode nem faturar nem seguir. Cai no mesmo estado terminal manual de sempre, e automatizar sua resolução segue fora de escopo.

Status: accepted

---

## Revisão — 2026-09-15: o template variante precisava de um dado que não existia

Esta ADR decidiu o **template variante** do `COMPLETED` na seção "Cliente é avisado, sem detalhe de item". A decisão continua valendo; o que faltava era como o Notifications saberia escolhê-lo.

Ele lê só a linha CDC de `orders` ([ADR notifications/0001](../../../notifications/docs/adr/0001-consume-order-cdc-topic.md)), e `fulfillment_status` é coluna de `order_items` — então o bit não chegava ao consumidor. Decidido em [ADR notifications/0005](../../../notifications/docs/adr/0005-partial-completion-template-selection.md): `orders` ganha a coluna **`partially_fulfilled`**, escrita pela Invoice Generation **na mesma transação da transição terminal**, e é dela que o template sai.

A obrigação que isto cria deste lado é a coluna e sua escrita; o modelo de dados do PRD foi atualizado junto. Esta ADR decidiu algo do lado do Notifications sem registrá-lo lá, e foi por isso que o dado faltou — o padrão a evitar é decidir sobre outro contexto sem que a decisão chegue no glossário dele.
