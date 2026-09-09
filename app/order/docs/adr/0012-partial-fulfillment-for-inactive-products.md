# Atendimento parcial: item com produto `INACTIVE` não é atendido, e o Order segue

O cadastro de produtos passou a ter [Product Status](../../../products/CONTEXT.md) (`ACTIVE`/`INACTIVE`). Isso criou uma pergunta que o Customers nunca criou, porque lá o cadastro não tem estado: **o que o Order faz com um item cujo produto existe mas não é mais vendável?**

Decidimos: **o item não é atendido, e o Order segue com os itens restantes.** O Order só é cancelado se nenhum item restar.

## Por que não cancelar o pedido inteiro

Era o caminho barato: a [ADR raiz 0002](../../../docs/adr/0002-compensating-flow-for-order-processing.md) já cancela o Order inteiro quando uma Processing Step hard-blocking falha, e reusar isso não custaria desenho nenhum.

Mas produto descontinuado não é falha de processamento. Um pedido de cinco itens em que um saiu de linha continua sendo um pedido válido de quatro itens, e recusá-lo inteiro joga fora trabalho e venda por uma razão que o cliente não controla. É a mesma lógica que fez o SKU inexistente travar em `AWAITING_MASTER_DATA` em vez de recusar a linha ([ADR 0011](0011-async-only-enrichment-await-master-data.md)): o desenho evita descartar pedido por dado ausente.

## O que isso custa, e não é pouco

**`order_items` ganha estado.** Hoje o item é dado puro — `sku`, `quantity`, snapshots, preço. Passa a ter situação (`FULFILLED` / `NOT_FULFILLED`) e motivo (`PRODUCT_INACTIVE`). Foi a opção escolhida contra duas alternativas piores: remover a linha apagaria a evidência de que o cliente pediu aquilo, e zerar a `quantity` esconderia o caso num número válido.

**As quatro etapas seguintes passam a operar sobre um total que mudou depois do import.** Credit Validation avalia crédito sobre menos, Stock reserva menos, Route calcula carga menor, Invoice fatura menos. Nenhuma delas foi desenhada sabendo que o conjunto de itens pode encolher no meio do caminho.

**O fluxo compensatório não sabe reverter item.** A ADR raiz 0002 compensa Order inteiro. Um Order que chega ao fim com itens não atendidos não precisa de compensação — mas um Order que precise compensar *depois* de ter itens não atendidos vai compensar apenas o que foi efetivamente reservado. Isso não está desenhado e é o ponto mais frágil desta decisão.

## Reverificação: o congelamento é o faturamento

O Product Status é lido na Product Enrichment, que é a primeira das cinco etapas. Entre ela e a Invoice Generation há espaço para o produto ser desativado — e a regra de negócio é que **até faturar, o pedido ainda pode mudar**.

Decidimos **reconferir o Product Status na Invoice Generation**, via Products Lookup síncrono, imediatamente antes de emitir. É o último ponto antes do congelamento, custa uma consulta, e dá a semântica pedida sem infraestrutura nova.

As alternativas foram descartadas com motivo:

- **Reverificar em toda etapa** multiplica chamadas por quatro para fechar uma janela de segundos.
- **Eventos de ciclo de vida no cadastro** (`ProductDeactivated`) é a solução tecnicamente correta e a mais cara: exige tópico, consumidor, ordenação e reprocessamento. Foi rejeitada na [ADR 0011](0011-async-only-enrichment-await-master-data.md) por não ter consumidor; agora tem um, mas um consumidor não justifica a infraestrutura enquanto uma consulta síncrona resolve.
- **Aceitar a janela** (só o enriquecimento verifica) contradiz a regra de negócio explícita.

Depois de faturado, nada altera o pedido — desativação posterior não alcança Order já faturado, e o Product Snapshot preserva o que foi vendido.

## O que fica em aberto

- **Notificação ao cliente** de item não atendido. O MS Notifications hoje dispara em mudança de status do Order, e item não atendido não muda status do Order. Não decidido.
- **Compensação parcial**, como descrito acima.
- **Todos os itens `INACTIVE`**: o Order é cancelado, mas por qual caminho — `CANCELLED` direto ou passando por `COMPENSATING`? Depende de já haver efeito colateral aplicado, e não foi desenhado.

Status: accepted
