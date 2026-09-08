# Pricing (Precificação)

Contexto dono do preço de venda dos produtos. Alimenta a Data Enrichment do Order com o valor unitário que vira Snapshot no Order Item. Não conhece pedido, cliente nem estoque — só responde quanto custa um SKU.

## Language

**Unit Price**:
Preço de venda de um SKU, em moeda única. É o que a Pricing Enrichment devolve pro Order gravar no Order Item. Vale para todos os clientes igualmente: **não existe preço por cliente**, tabela negociada nem faixa por quantidade — o preço é do produto, não da relação comercial.
_Avoid_: preço de tabela (implica existir mais de uma tabela), preço negociado, valor do item (é `unit_price × quantity`, coisa diferente), custo

**Currency**:
Moeda do Unit Price. Hoje sempre a mesma para o sistema inteiro — o campo existe para deixar a moeda explícita no dado, não para suportar mistura. Pedido com itens em moedas diferentes não é representável: não há taxa de câmbio em lugar nenhum do desenho.
_Avoid_: câmbio, conversão, moeda do cliente, multi-moeda

**Pricing Enrichment**:
A Processing Step do Order que este contexto executa: dada uma lista de SKUs, devolve o Unit Price e a Currency de cada um. É a única coisa que este contexto faz pelo Order — não calcula total, não aplica desconto, não valida crédito e não sabe quem é o cliente.
_Avoid_: Data Enrichment (é o sub-workflow inteiro, que inclui Customers e Products — Pricing Enrichment é só a parte deste contexto), cálculo de preço, cotação

**Price Not Found**:
O SKU não tem preço cadastrado. Não recusa o pedido: a task fica travada até o preço existir e a task ser reexecutada, mesmo caminho de [Product Not Found](../products/CONTEXT.md) e [Customer Not Found](../customers/CONTEXT.md) — ver [ADR order/0011](../order/docs/adr/0011-async-only-enrichment-await-master-data.md). Indisponibilidade deste contexto nunca é Price Not Found.
_Avoid_: preço zerado (zero é um preço válido e não é ausência), produto sem preço como erro de validação, preço inválido

**Discount**:
Não existe. Nenhum desconto, promoção, cupom ou condição comercial é aplicado em lugar nenhum do sistema — o valor do Order Item é `Unit Price × quantity`, sem exceção. Termo listado só pra impedir que reapareça por engano.
_Avoid_: promoção, campanha, cupom, condição comercial, preço promocional

**Tax**:
Não existe aqui. Se imposto entrar no desenho um dia, é do contexto Invoice, que emite o documento fiscal — este contexto devolve preço de venda e nada mais.
_Avoid_: imposto, ICMS, tributação, preço com imposto

**Price History**:
Não existe. Este contexto conhece só o preço atual de cada SKU. O histórico de quanto custou num pedido vive do outro lado, como Snapshot no Order Item — e é lá que ele é imutável.
_Avoid_: vigência, preço na data, tabela histórica, versionamento de preço
