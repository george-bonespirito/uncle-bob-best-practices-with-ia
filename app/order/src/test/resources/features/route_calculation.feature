@pending
Feature: Cálculo de rota e limite do que o sistema acompanha

  Como sistema
  Quero planejar a entrega e parar aí
  Para não prometer um acompanhamento de carga que este sistema não faz

  Scenario: Rota calculada grava plano e previsão
    Given um Order "#123" com Credit Validation e Stock Validation concluídas
    And o Delivery Address do Snapshot é atendido por alguma rota
    When a etapa "Route Calculation" executa
    Then routeId, trackingCode e estimatedDelivery são gravados no Order
    And o Order segue para "Invoice Generation"

  Scenario: Endereço sem rota cancela e compensa
    Given um Order "#123" com Credit Hold ativo e Stock Reservation ativa
    And nenhuma rota atende o Delivery Address do Snapshot
    When a etapa "Route Calculation" falha permanentemente
    Then o Order muda para "COMPENSATING"
    And a task compensatória "release-credit-hold" libera o crédito
    And a task compensatória "release-stock-reservation" libera o estoque
    And o Order termina em "CANCELLED"

  Scenario: A carga considera só os itens atendidos
    Given um Order "#123" com 3 itens, sendo o item 2 "NOT_FULFILLED"
    When a etapa "Route Calculation" executa
    Then o peso e as dimensões do item 2 não entram no cálculo de carga
    And os Physical Attributes vêm do MS Products via consulta síncrona

  Scenario: Rota não é recalculada quando o pedido encolhe depois
    Given um Order "#123" com rota calculada para 3 itens
    When a Invoice Generation marca o item 3 como "NOT_FULFILLED"
    Then o routeId permanece o mesmo
    And o estimatedDelivery permanece o mesmo
    And a carga fica superdimensionada, o que é aceito

  Scenario: Estimated Delivery é congelada
    Given um Order "#123" com estimatedDelivery calculada
    When o tempo passa e a entrega avança
    Then o estimatedDelivery não é atualizado
    And nenhum evento de progresso de entrega é recebido

  Scenario: O Order termina no faturamento, não na entrega
    Given um Order "#123" faturado, com trackingCode gravado
    When a transportadora entrega a carga ao cliente
    Then o Order permanece em "COMPLETED"
    And nenhum status de entrega existe no sistema
    And nenhuma notificação de entrega é enviada
