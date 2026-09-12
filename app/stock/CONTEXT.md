# Stock (Estoque)

Contexto dono da quantidade disponível de cada SKU e das reservas feitas sobre ela. Executa a Stock Validation do Order — a etapa que garante que o que foi pedido existe fisicamente antes de o pedido seguir para entrega e faturamento.

## Language

**Stock Balance**:
Quantidade física de um SKU em poder da empresa. Não é o que pode ser vendido: parte dela pode estar comprometida com pedidos em andamento.
_Avoid_: estoque (sem qualificar físico ou disponível — a ambiguidade é o erro que este glossário existe pra evitar), saldo, quantidade

**Available Quantity**:
`Stock Balance` menos o que está reservado. É contra este número que a Stock Validation decide, e é **derivado** — não existe como campo mantido à mão, porque dois números que deveriam concordar sempre acabam discordando.
_Avoid_: disponível (sem qualificar), saldo livre, estoque virtual

**Stock Reservation**:
Compromisso de unidades de um SKU com um Order específico. Tem identidade própria (`stockReservationId`), que é o que a compensação recebe para liberar. Enquanto existe, as unidades saem do Available Quantity sem sair do Stock Balance.
_Avoid_: bloqueio, alocação, separação (é operação de armazém, coisa diferente), baixa de estoque

**Stock Validation**:
A Processing Step do Order que este contexto executa: dado o conjunto de itens atendidos de um Order, reserva tudo ou não reserva nada. É **hard-blocking** — falta de estoque cancela o pedido, ao contrário de produto inativo, que apenas deixa o item de fora ([ADR order/0014](../order/docs/adr/0014-out-of-stock-cancels-order.md)).
_Avoid_: consulta de estoque (não é consulta, é reserva), verificação de disponibilidade, Data Enrichment (a Stock Validation vem depois e não é enriquecimento)

**Out of Stock**:
Available Quantity insuficiente para a quantidade pedida de um item. É condição **transitória** — o SKU volta a ter estoque quando chega reposição, e é exatamente isso que distingue Out of Stock de [Product `INACTIVE`](../products/CONTEXT.md), que é permanente. Os dois têm desfechos opostos no Order de propósito.
_Avoid_: produto indisponível (colide com `INACTIVE`), sem estoque como sinônimo de descontinuado, ruptura

**Partial Quantity**:
Não existe. Item que pede 10 com 4 disponíveis é Out of Stock, não item parcialmente atendido — reservar 4 criaria uma terceira situação de item que nem o `order_items` nem a fatura sabem representar.
_Avoid_: atendimento parcial de item (o Order tem Partial Fulfillment, que é item inteiro fora, nunca quantidade quebrada), backorder, saldo a faturar

**Reservation Release**:
Liberação de uma Stock Reservation, devolvendo as unidades ao Available Quantity. Acontece **só** por ação compensatória explícita, identificada pelo `stockReservationId`; liberar um id já liberado é no-op, não erro. **Não há expiração automática** — reserva presa alguém percebe, reserva que volta sozinha vira venda dupla silenciosa.
_Avoid_: TTL, expiração, timeout de reserva, rollback automático

**Stock Movement**:
Não existe aqui. Entrada de mercadoria, inventário e baixa por expedição são de outro domínio, fora do escopo deste sistema. Este contexto conhece o Stock Balance atual e as reservas sobre ele, e nada sobre como o saldo chegou a esse valor.
_Avoid_: entrada, saída, inventário, movimentação, kardex

**Warehouse**:
Não existe. O estoque é único e sem localização — não há depósito, endereço de armazenagem nem separação por filial. Se um dia houver, a Available Quantity deixa de ser um número só e a Stock Validation precisa saber de onde reservar.
_Avoid_: depósito, filial, centro de distribuição, localização de estoque
