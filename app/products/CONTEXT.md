# Products (Cadastro de Produtos)

Contexto dono do cadastro de produtos do sistema de supply chain: fonte de verdade do que é vendável, como se chama, a que categoria pertence e quanto ocupa num veículo. Alimenta a Data Enrichment do Order com o dado que vira Snapshot do lado de lá, e é consultado pelo Route para calcular carga.

## Language

**Product**:
Item vendável do catálogo. Aggregate root deste contexto — identidade própria (`sku`), cadastro autônomo. Não existe hierarquia produto-pai/variante: um Product é a coisa final que o cliente compra, e tamanho ou cor, se um dia importarem, serão Products distintos com SKUs distintos.
_Avoid_: item (é a linha do pedido, do lado do Order), SKU como sinônimo de Product (SKU é a identidade, não a coisa), mercadoria, artigo

**SKU**:
Identidade do Product, emitida por este contexto e usada pelo mundo todo pra referenciá-lo — inclusive pela planilha de import do Order, que traz o `sku` já resolvido. Não existe código alternativo por sistema de origem, nem conversão.
_Avoid_: código do produto, product_code, product_id, EAN, código de barras

**Name**:
Nome curto do Product — o que se lê numa linha de pedido. É o que a Product Enrichment copia pro Product Snapshot do Order.
_Avoid_: descrição (é outro campo deste contexto, mais longo — ver Description), título, nome fantasia, display name

**Description**:
Texto longo do Product, com suas características. Existe neste contexto e **não** vai pro Product Snapshot: a linha de pedido mostra Name, e quem precisar do texto completo consulta este contexto pelo `sku`.
_Avoid_: nome longo, detalhamento, especificação técnica, Name (são campos diferentes e a distinção é o ponto)

**Category**:
Classificação comercial do Product. Copiada pro Product Snapshot junto com o Name.
_Avoid_: departamento, família, grupo de produto, linha

**Physical Attributes**:
Peso e dimensões (comprimento, largura, altura) do Product. Obrigatórios no cadastro: os dois restringem carga de veículo por motivos diferentes — peso por limite de eixo, cubagem por espaço — e um Product sem eles é inútil pro Route. Nunca vão pro Product Snapshot: o Order não roteiriza.
_Avoid_: peso/medidas como opcionais, cubagem (é derivada das dimensões, não um campo), volumetria

**Product Status**:
Situação do Product no catálogo — `ACTIVE` ou `INACTIVE`. Diferente de existir: um SKU `INACTIVE` existe, tem cadastro completo e histórico, e simplesmente não é mais vendável.
_Avoid_: produto excluído, deletado, descontinuado como flag separada, disponibilidade (é estoque, do Stock)

**Product Enrichment**:
A Processing Step do Order que este contexto executa: dado um `sku`, devolve Name, Category e o Product Status pro Order. É a única coisa que este contexto faz pelo Order — não reserva estoque, não precifica, não decide o que o Order faz com um `INACTIVE`.
_Avoid_: Data Enrichment (é o sub-workflow inteiro, que inclui Customers e Pricing — Product Enrichment é só a parte deste contexto), consulta de produto, lookup de SKU

**Products Lookup**:
Consulta síncrona a este contexto, fora do fluxo de Processing Step. Dois consumidores: o Route, buscando Physical Attributes pra calcular carga, e a Invoice Generation, reconferindo o Product Status imediatamente antes de faturar — ver [ADR order/0012](../order/docs/adr/0012-partial-fulfillment-for-inactive-products.md).
_Avoid_: API do Products (genérico), consulta de catálogo, product service

**Product Not Found**:
O `sku` não existe no cadastro. Não recusa o pedido: a task fica travada até o produto ser cadastrado e a task reexecutada — ver [ADR order/0011](../order/docs/adr/0011-async-only-enrichment-await-master-data.md). Indisponibilidade deste contexto nunca é Product Not Found: são coisas distintas e o consumidor precisa distingui-las. **Não confundir com `INACTIVE`**, que é produto que existe e não é vendável — os dois têm desfechos opostos no Order.
_Avoid_: produto inválido, SKU rejeitado, produto indisponível, erro de enriquecimento

**Product Price**:
Não existe aqui. Preço é do contexto Pricing, e este contexto nunca o conhece — nem histórico, nem tabela, nem preço sugerido. Termo listado só pra impedir que reapareça por engano.
_Avoid_: preço de tabela, preço base, custo, valor unitário

**Stock**:
Não existe aqui. Quantidade disponível é do contexto Stock. Um Product `ACTIVE` com estoque zero continua `ACTIVE` — as duas coisas não se falam neste contexto.
_Avoid_: disponibilidade, saldo, quantidade em estoque
