# Route entrega um plano, não um acompanhamento: o Order termina no faturamento

O contrato do MS Route devolve `routeId`, `trackingCode` e `estimatedDelivery`. A presença de um código de rastreio sugere que o sistema acompanha a entrega. **Não acompanha**, e a distinção define o ciclo de vida do Order.

## O Order termina em `COMPLETED`, no faturamento

Não existem `SHIPPED`, `DELIVERED` nem evento de progresso de entrega. O `trackingCode` é dado repassado ao cliente para consultar **na transportadora** — não é estado deste sistema.

Acrescentar estados de entrega mudaria o ciclo de vida do Order inteiro, não o contrato do Route: os status estão no [glossário do Order](../../CONTEXT.md), o `status_change_notification.feature` notifica sobre eles, e o MS Notifications dispara em cada mudança. Seria funcionalidade nova, com origem de evento nova — a transportadora —, que este sistema não integra.

## `Estimated Delivery` é Snapshot, não promessa

Calculada uma vez e congelada, como preço e dado de produto.

Atualizá-la exigiria os mesmos eventos de progresso que não existem, e o nome importa: "prazo de entrega" sugere compromisso contratual, "previsão estimada" não. O glossário lista `prazo` e `SLA` como termos a evitar por isso.

## Falha cancela e compensa

A Route Calculation é a quarta hard-blocking, com crédito e estoque já comprometidos. Falha permanente — nenhuma rota atende aquele endereço — cancela o Order e compensa os dois.

Mesmo argumento das etapas anteriores: esperar travaria recurso de terceiros por tempo indefinido. E endereço não atendido raramente se resolve sozinho, ao contrário de estoque ou crédito.

## A rota não é recalculada quando o pedido encolhe

A carga é dimensionada sobre os itens atendidos no momento do cálculo. Um item pode virar `NOT_FULFILLED` depois, na reverificação de Product Status da Invoice Generation — e a rota fica dimensionada para carga maior.

Aceitamos: carga superdimensionada cabe no veículo, e recalcular acrescentaria uma quarta responsabilidade à Invoice Generation, que já reconfere status e converte o Credit Hold.

Isto entra na mesma família de pendências abertas pelo atendimento parcial — junto com a compensação parcial ([ADR 0012](0012-partial-fulfillment-for-inactive-products.md)) e o Credit Hold maior que a fatura ([ADR 0015](0015-credit-hold-lifecycle.md)). Os três são o mesmo fenômeno: **efeito colateral dimensionado antes de o pedido encolher.** Se algum dia forem resolvidos, devem ser resolvidos juntos.

## O que continua fora

Cálculo real de rota permanece fora de escopo, como o PRD já declarava. Este contexto é contrato: transportadora, cotação e valor de frete não existem, e estão listados no glossário como inexistentes para que ninguém os assuma.

Status: accepted
