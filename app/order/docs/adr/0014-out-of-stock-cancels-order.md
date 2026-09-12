# Falta de estoque cancela o Order; produto inativo não. A assimetria é deliberada

A [ADR 0012](0012-partial-fulfillment-for-inactive-products.md) criou atendimento parcial: item com produto `INACTIVE` não é atendido e o pedido segue. Isso levanta imediatamente a pergunta de por que falta de estoque continua cancelando o pedido inteiro, como a FR-06 sempre disse.

Decidimos **manter o cancelamento**, e a assimetria é a decisão, não um resíduo.

## O que separa os dois casos

**`INACTIVE` é permanente.** O produto saiu de linha; aquele item nunca mais será vendável. Deixá-lo de fora é a única forma de o pedido andar, e não se perde nada — não há futuro em que ele seria atendido.

**Falta de estoque é transitória.** O SKU volta a ter saldo quando chega reposição. Marcar o item como não atendido descartaria silenciosamente uma demanda que poderia ser servida amanhã, e o cliente não teria como saber que pediu algo que o sistema desistiu de entregar.

Cancelar devolve o pedido a quem pode reenviá-lo quando houver estoque. É o desfecho ruidoso, e ruído aqui é a propriedade desejada.

## O que a reserva cobre

O PRD dizia que a reserva é "atômica pro pedido inteiro — não há reserva parcial por item". Esse texto é anterior à ADR 0012 e ficou impreciso: **a reserva é atômica sobre o conjunto de itens `FULFILLED`**, não sobre todos os itens da planilha. Um item `NOT_FULFILLED` por produto inativo não participa da reserva, e nunca participou — o `partial_fulfillment.feature` já afirmava isso.

Atômica continua valendo no que importa: dentro daquele conjunto, ou tudo é reservado ou nada é, com um `stockReservationId` só.

## Cadastro ausente não é falta de estoque

Um SKU **sem registro de estoque** não é o mesmo que um SKU com saldo insuficiente. O primeiro é cadastro faltando; o segundo é condição do negócio.

Cadastro ausente segue o caminho dos irmãos — `Product Not Found` e `Price Not Found`: trava em `AWAITING_MASTER_DATA` até alguém cadastrar, sem cancelar nada. Só saldo insuficiente com registro existente é Out of Stock.

A distinção importa porque os dois desfechos são opostos, e confundi-los cancelaria pedidos por um cadastro que ninguém fez ainda — exatamente o que a [ADR 0011](0011-async-only-enrichment-await-master-data.md) decidiu evitar.

## Quantidade parcial não existe

Item que pede 10 com 4 disponíveis é Out of Stock, não item parcialmente atendido.

Reservar 4 criaria uma terceira situação de item — nem `FULFILLED` nem `NOT_FULFILLED` —, e nada no desenho sabe representá-la: `order_items` tem uma `quantity` só, a fatura cobraria diferente do pedido, e o `fulfillment_status` criado pela ADR 0012 seria insuficiente na primeira semana de uso.

## Reserva não expira

Liberação acontece só por ação compensatória explícita, identificada pelo `stockReservationId`.

TTL foi rejeitado pelo mesmo raciocínio que a infra aplicou ao `claim` do mcp-bus, e que vale igual aqui: **reserva presa alguém percebe** — o estoque não bate e vira investigação. **Reserva que volta sozinha vira venda dupla silenciosa**, porque o Order segue acreditando que tem estoque garantido enquanto as unidades já foram para outro pedido.

Entre um problema visível e um invisível, escolhemos o visível. `COMPENSATION_FAILED` já está declarado como resolução manual, e uma reserva presa é exatamente o tipo de coisa que deve aparecer nessa fila.

## Consequência aceita

Um pedido de cinquenta itens em que um único SKU está em falta é cancelado inteiro, e o crédito já reservado é compensado. É trabalho desperdiçado quando a reposição chega no dia seguinte.

Aceitamos porque a alternativa — atender parcialmente — transforma uma indisponibilidade temporária numa perda permanente de linha de pedido, sem ninguém decidir isso explicitamente. Se o volume de cancelamento por falta de estoque se mostrar alto na prática, o caminho não é atendimento parcial: é dar ao cliente a opção de esperar, que é uma funcionalidade nova e não um ajuste.

Status: accepted
