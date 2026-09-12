# Route (Roteirização e Envio)

Contexto dono do planejamento de entrega do pedido: qual rota atende o endereço, qual o código de rastreio e qual a previsão. Executa a Route Calculation — a quarta Processing Step hard-blocking.

**O cálculo de rota em si está fora de escopo deste sistema** (PRD, "Out of scope"): este contexto é contrato, não algoritmo. O glossário define a linguagem; a lógica de roteirização não está desenhada e não deve ser inventada por quem implementar.

## Language

**Route**:
Plano de entrega de um Order — como a carga chega ao Delivery Address do cliente. Tem identidade própria (`routeId`).
_Avoid_: rota (sem qualificar, colide com o sentido geográfico), frete, entrega (é o evento, não o plano), viagem

**Route Calculation**:
A Processing Step que este contexto executa: dado o endereço do Snapshot do Order e os [Physical Attributes](../products/CONTEXT.md) dos itens atendidos, devolve `routeId`, Tracking Code e Estimated Delivery. É **hard-blocking**: falha cancela o Order e compensa crédito e estoque.
_Avoid_: cálculo de frete (valor não é calculado aqui — nem em lugar nenhum deste sistema), otimização de rota, planejamento logístico

**Tracking Code**:
Código que o cliente usa para consultar a entrega **na transportadora**. É dado repassado, não estado deste sistema: emiti-lo não significa que alguém aqui saiba onde a carga está.
_Avoid_: rastreamento (sugere acompanhamento ativo), status de entrega, código de postagem

**Estimated Delivery**:
Data prevista de entrega, calculada uma vez e **congelada** — é Snapshot, como preço e dado de produto. Não é promessa contratual nem se atualiza conforme a entrega avança, porque não existem eventos de progresso neste sistema.
_Avoid_: prazo de entrega (sugere compromisso), SLA, data de entrega (sem "estimada"), previsão atualizada

**Delivery Tracking**:
Não existe. Este sistema não acompanha o que acontece depois do faturamento: não há `SHIPPED`, `DELIVERED`, nem evento de progresso. O ciclo de vida do Order termina em `COMPLETED`, no faturamento — ver [ADR order/0017](../order/docs/adr/0017-route-is-a-plan-not-a-delivery.md).
_Avoid_: status de entrega, entregue, em trânsito, ocorrência de entrega

**Carrier**:
Não existe como conceito modelado. Não há cadastro de transportadora, seleção por cotação nem contrato de frete. Se um dia houver, é decisão de escopo do sistema, não deste contexto.
_Avoid_: transportadora, prestador, cotação, tabela de frete

**Freight Cost**:
Não existe. Nenhum valor de frete é calculado, cobrado ou somado ao Order Total. O [Order Total](../order/CONTEXT.md) é a soma dos itens atendidos e nada mais.
_Avoid_: frete, valor de entrega, custo logístico
