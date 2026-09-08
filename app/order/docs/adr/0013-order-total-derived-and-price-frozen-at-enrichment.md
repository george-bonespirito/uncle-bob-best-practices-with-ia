# Total do Order é derivado; preço congela no enriquecimento, ao contrário do Product Status

Duas decisões sobre o valor de um pedido, tomadas juntas porque compartilham o mesmo argumento e porque a segunda só faz sentido em contraste com a [ADR 0012](0012-partial-fulfillment-for-inactive-products.md).

## O total do Order não é uma coluna

A tabela `orders` nunca teve total, e a Credit Validation precisa de um valor para submeter. A escolha era gravar `total_amount` quando a Pricing Enrichment conclui, ou derivar a soma dos itens atendidos toda vez que alguém precisar.

Decidimos **derivar, sem armazenar**.

O argumento decisivo é o atendimento parcial. Um item pode virar `NOT_FULFILLED` **depois** da Pricing — inclusive na Invoice Generation, quando o Product Status é reconferido. Um total gravado ficaria errado exatamente nos casos que a ADR 0012 existe para tratar, e ficaria errado em silêncio: nada falharia, o número só passaria a não corresponder aos itens.

Recalcular a cada mudança é a mesma decisão com o problema movido para "lembrar de recalcular", que é a classe de bug que este projeto já encontrou de outras formas.

Custo aceito: toda leitura que precise do total soma os itens. Com no máximo algumas dezenas de itens por pedido, é irrelevante — e se um dia deixar de ser, um campo derivado materializado é uma otimização com invalidação explícita, não o desenho inicial.

## O preço congela no enriquecimento — e o status não

A [ADR 0012](0012-partial-fulfillment-for-inactive-products.md) mandou reconferir o Product Status na Invoice Generation, porque "até faturar, o pedido ainda pode mudar". A pergunta natural é por que o preço não segue a mesma regra.

Decidimos que **não segue**: o `unit_price` é gravado uma vez, na Pricing Enrichment, e não é reconferido em lugar nenhum.

A distinção é entre **atendibilidade** e **valor**:

- Se o produto ainda pode ser vendido é uma condição do mundo que muda sem o pedido pedir, e vender algo descontinuado é um erro que o sistema deve evitar até o último instante.
- Quanto custa foi determinado quando o pedido foi processado. Reprecificar antes de faturar mudaria o valor de um pedido em curso sem ninguém ter pedido — e faria a Credit Validation ter avaliado crédito sobre um número que não é o que será cobrado.

O `unit_price` no Order Item é um Snapshot, exatamente como o `product_snapshot`: registro do que valia no momento, não referência ao dado vivo. O contexto Pricing, coerentemente, **não guarda histórico** — o histórico de preço de um pedido é o Snapshot, e é do lado do Order que ele é imutável.

## Consequência: a assimetria é deliberada e precisa continuar escrita

Um leitor futuro vai encontrar um sistema que reconfere status e não reconfere preço, e vai suspeitar de esquecimento. Não é. Se alguém propuser "reconferir preço também, por consistência", a resposta é que consistência aqui seria copiar o mecanismo ignorando o motivo dele.

O caminho inverso — parar de reconferir o status, por simetria — está igualmente errado, e o motivo está na ADR 0012.

Status: accepted
