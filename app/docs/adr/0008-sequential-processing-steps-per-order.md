# Processing Steps sequenciais por pedido, com fork-join preservado dentro da Data Enrichment

O design original ([ADR raiz 0002](./0002-compensating-flow-for-order-processing.md)) punha 4 Processing Steps hard-blocking em fork-join — Data Enrichment, Credit Validation, Stock Validation e Route Calculation em paralelo — e só depois Invoice Generation. A Data Enrichment é, ela própria, um `SUB_WORKFLOW` com fork-join de 3 tasks (Customers, Products, Pricing), uma por microsserviço ([ADR raiz 0001](./0001-worker-polling-for-processing-steps.md)).

Decidimos que **as 5 Processing Steps de um mesmo Order passam a executar em sequência**:

```
Data Enrichment → Credit Validation → Stock Validation → Route Calculation → Invoice Generation
```

**Dentro da Data Enrichment, o fork-join das 3 tasks é preservado.** Customers, Products e Pricing são independentes entre si — buscam dado de contextos diferentes e escrevem campos diferentes do agregado.

## O que "sequencial" significa aqui

Sequencial **por pedido**, não no sistema. Há N Orders em voo ao mesmo tempo, processados por múltiplas instâncias de MS Order; o Orkes garante a ordem das etapas **dentro de um Order**, e não tem opinião sobre a relação entre Orders diferentes. A concorrência que este projeto existe para exercitar não diminui.

## Por que

**Ordem lógica.** No fork-join, Stock Validation reservava estoque de um SKU que a Data Enrichment ainda não tinha validado, e Route Calculation calculava rota sem o endereço de entrega — que vem do snapshot de Customer, produzido pela própria Data Enrichment. As etapas dependiam de dado que podia ainda não existir, e o paralelismo escondia isso.

**Efeito colateral não fica preso.** Com a Data Enrichment primeiro, um Order que trava esperando cadastro de cliente ou produto ([ADR order/0011](../../order/docs/adr/0011-async-only-enrichment-await-master-data.md)) não segura reserva de estoque nem hold de crédito, porque essas etapas nem começaram. É o que permite a espera ser indefinida em vez de exigir prazo máximo com compensação automática.

**Compensação determinística.** Antes, o conjunto de etapas a compensar dependia de quais tinham terminado no instante da falha — nenhuma, uma ou as duas com efeito colateral, dependendo do timing. Agora é a fila: compensam-se as etapas anteriores à que falhou, sempre as mesmas para a mesma posição de falha.

## O que isso custa — e o que não custa

A avaliação inicial de que isso derrubaria o núcleo do projeto estava errada em quase tudo. Do que motivou o design:

- **`SKIP LOCKED`** — opera na fila `order_update_event`, que acumula eventos de muitos Orders diferentes; workers disputam linhas dessa fila ([ADR order/0003](../../order/docs/adr/0003-kafka-staging-skip-locked-for-processing-results.md)). Intacto.
- **Virtual threads** — ingestão da planilha e pool de workers. Intacto.
- **Concorrência entre instâncias** — N Orders em voo, tópico não particionado por `order_id`. Intacto.
- **Optimistic locking / Lost Update** — **intacto, mas muda de lugar.** O cenário canônico deixa de ser "Enrichment e Credit Validation escrevendo no mesmo Order" e passa a ser as 3 tasks da Data Enrichment: Customers escreve `customer_snapshot` no Order, Products escreve `product_snapshot` e Pricing escreve `unit_price` nos `order_items`, e todas incrementam o mesmo `version` do agregado (nota de escopo da [ADR order/0001](../../order/docs/adr/0001-optimistic-locking-for-order-writes.md)). O Lost Update continua sendo um problema real deste contexto.

O que de fato se perde é a disputa entre Processing Steps distintas — que era o exemplo mais legível do fenômeno, mas não o único.

## Alternativas rejeitadas

**Manter o fork-join das 4 steps.** Preserva o exemplo mais didático de Lost Update, mas mantém a ordem ilógica (validar estoque de SKU não validado) e exige prazo máximo de espera com cancelamento automático para não deixar estoque preso — mecanismo novo, inteiramente a serviço de um problema que a sequência elimina.

**Sequencial total, incluindo as 3 tasks da Data Enrichment.** Um caminho linear único, o diagrama mais simples possível. Rejeitada porque as 3 tasks não têm dependência entre si — serializá-las tripilca a latência da etapa sem ganho de correção, e elimina o último ponto do sistema onde duas escritas disputam o mesmo agregado, aí sim esvaziando o `version`.

## Consequências

- [ADR raiz 0002](./0002-compensating-flow-for-order-processing.md) descreve o fork-join das 4 e precisa ser lida com esta ADR ao lado — a decisão de quais etapas compensam e como não muda; a ordem em que elas rodam, sim.
- O Order ganha o status `AWAITING_MASTER_DATA` (ADR order/0011), possível apenas durante a Data Enrichment.
- Latência por pedido aumenta: as etapas somam em vez de sobrepor. Irrelevante neste sistema — o import é assíncrono e o usuário não espera resposta.

Status: accepted
