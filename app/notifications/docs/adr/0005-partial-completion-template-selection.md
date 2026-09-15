# O fato que escolhe o template variante viaja na própria linha do Order

A [ADR order/0018](../../../order/docs/adr/0018-order-shrinkage-at-invoice.md) decidiu que o cliente recebe um **template variante** do `COMPLETED` — "pedido concluído, com itens não atendidos", sem listar quais — quando o pedido encolhe no faturamento.

A decisão é sobre o que este contexto envia, e foi tomada no contexto Order. Deste lado da cerca ela nunca chegou: não há ADR aqui, o glossário não tem o termo, e `Message Template` continua dizendo que o template carrega "número do pedido e status, e nada além disso".

E não é só papel faltando. Este contexto lê **a linha CDC de `orders`** — `op: u` e `before.status != after.status` ([ADR notifications/0001](0001-consume-order-cdc-topic.md)) — enquanto `fulfillment_status` é coluna de **`order_items`**, a tabela filha. Com o que o consumidor recebe hoje, escolher entre os dois templates é impossível: o bit não existe no evento.

É a mesma forma do buraco que a [ADR 0003](0003-lookup-contact-at-send-time.md) fechou — o WhatsApp Phone que não existia em lugar nenhum — e pela mesma causa: nenhum documento era dono da pergunta "de onde vem esse dado".

Decidimos: **o Order passa a carregar o fato na própria linha**, como `partially_fulfilled`, escrito pela Invoice Generation na mesma transação da transição terminal para `COMPLETED`. O Notifications escolhe o template a partir dele.

Um bit, nada sobre itens. O flag é escrito uma vez e reflete a composição final do pedido, tenha o item caído na Product Enrichment ([ADR order/0012](../../../order/docs/adr/0012-partial-fulfillment-for-inactive-products.md)) ou na reverificação do faturamento (0018).

## Por que não consultar o Order no envio

Seria o caminho da ADR 0003, e a regra que ela deixou é justamente o que o proíbe: **dado de contato se consulta no dono porque estar desatualizado é indistinguível de estar quebrado; dado de negócio do pedido viaja com o pedido.** Atendimento parcial é dado de negócio, e está congelado — a Invoice é terminal ([ADR order/0016](../../../order/docs/adr/0016-invoice-is-internal-and-terminal.md)).

Além disso criaria uma dependência de runtime Notifications → Order que hoje não existe (a única é Customers), para ler um valor que não pode mais mudar.

## Por que não um décimo status

`COMPLETED_PARTIAL` resolveria pelo mecanismo que este contexto já tem, mas faria o enum de status carregar um **adjetivo** em vez de um estado de ciclo de vida: o pedido está tão terminado quanto qualquer outro `COMPLETED`, e todo consumidor de status — o workflow do Orkes inclusive — teria que aprender um status a mais que significa o mesmo.

A revisão de 2026-09-13 da [ADR 0004](0004-notifiable-statuses-and-idempotency.md) já registrou o preço de cada status novo: alguém precisa dizer aqui de que lado ele cai, senão quem implementa decide sozinho.

## Por que não consumir o CDC de `order_items`

Dois streams sem garantia de ordem entre si, para obter um booleano. O evento do item pode chegar depois do evento de status, e a notificação sairia com o template errado — ou ficaria esperando algo que talvez não venha.

## Por que armazenar um derivado aqui não contradiz a ADR order/0013

A [ADR order/0013](../../../order/docs/adr/0013-order-total-derived-and-price-frozen-at-enrichment.md) recusou guardar o Order Total em coluna, e o motivo era concreto: o atendimento parcial faria um total gravado ficar **errado em silêncio**.

Esse modo de falha é obsolescência, e aqui ele não pode acontecer — o flag é escrito na transição terminal, depois da qual nada muda. A assimetria é deliberada: guarda-se derivado quando aquilo de que ele deriva está congelado.

## A Notification Key não muda

Continua `(order_id, novo_status)`. O variante é **escolha de template dentro de uma transição**, não uma transição nova nem uma segunda mensagem — conclusão parcial gera **uma** Notification, como qualquer outra.

O conteúdo também não muda de natureza: número do pedido, status, e o fato de algo não ter sido atendido. Nenhum item, nenhum valor — a regra de PII da ADR 0004 continua inteira.

## O que esta ADR não decide

O caminho até `CANCELLED` quando **todos** os itens caem já está decidido (0018, e a regra de efeito colateral da [ADR raiz 0002](../../../docs/adr/0002-compensating-flow-for-order-processing.md)); aqui nada muda, porque `CANCELLED` já é Notifiable Status e não tem variante.

Status: accepted
