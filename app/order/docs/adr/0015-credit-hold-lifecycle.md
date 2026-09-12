# Ciclo de vida do Credit Hold: criado na validação, convertido no faturamento, liberado só por compensação

A Credit Validation cria um `creditHoldId` e a compensação o libera. O que faltava era o caminho feliz: o Order chega a `COMPLETED` e o hold ficava lá, sem nada definindo o quê.

## Hold vira dívida no faturamento

Decidimos que a **emissão da nota fiscal converte o Credit Hold em dívida real** — o valor sai do Available Credit de forma permanente, em vez de continuar como compromisso reversível.

As alternativas são piores de formas opostas:

**Liberar o hold no `COMPLETED`** devolveria crédito de uma venda que aconteceu. O cliente compraria de novo sem ter pago a primeira, e o limite deixaria de limitar qualquer coisa.

**Deixar o hold ativo para sempre** é o mesmo erro sem nome: o crédito fica consumido, mas por um compromisso que ninguém sabe se ainda é compromisso ou já virou dívida. Na primeira divergência de saldo, não há como saber o que aquele hold representa.

A conversão é o evento que fecha o ciclo, e o faturamento é o momento certo: é quando a venda deixa de ser reversível pelo sistema.

## `REJECTED` cancela, não espera

Crédito insuficiente é transitório — o cliente paga e o limite libera. Isso o aproxima de falta de estoque, e a pergunta de por que não esperar é legítima.

A razão é mais forte aqui do que no Stock: **um pedido esperando crédito seguraria estoque reservado por tempo indefinido.** A Credit Validation vem antes da Stock Validation na sequência, mas um Order que espera não libera o que já consumiu, e a espera não tem prazo — depende de o cliente pagar. Falta de estoque cancela e não segura nada de terceiros; crédito insuficiente cancela pelo motivo mais forte ainda.

## Cadastro ausente espera

`Credit Account Not Found` — o Customer sem conta de crédito — não é decisão de negócio, é cadastro faltando. Trava em `AWAITING_MASTER_DATA`, como `Product Not Found`, `Price Not Found` e `Stock Record Not Found`.

Tratar como limite zero cancelaria o pedido de todo cliente novo até alguém criar a conta, que é exatamente o que a [ADR 0011](0011-async-only-enrichment-await-master-data.md) decidiu evitar.

## Hold não expira

Liberação só por `release-credit-hold`, com `creditHoldId`, idempotente.

Mesmo raciocínio do Stock e do `claim` do mcp-bus: **hold preso alguém percebe** — o crédito do cliente não bate e vira investigação. **Hold que volta sozinho vira venda sem crédito**, silenciosamente, porque o limite deixa de refletir o que foi comprometido.

## O que fica em aberto

**Qual valor o hold cobre quando o Order Total muda depois.** O [Order Total](0013-order-total-derived-and-price-frozen-at-enrichment.md) é derivado dos itens `FULFILLED`, e um item pode virar `NOT_FULFILLED` na Invoice Generation — depois de o hold existir. O hold ficaria maior que a fatura.

Não decidimos se a conversão ajusta o valor ou converte o hold inteiro. É consequência direta do atendimento parcial e entra na mesma família da compensação parcial, que a [ADR 0012](0012-partial-fulfillment-for-inactive-products.md) já deixou aberta.

Status: accepted
