Feature: Reservation Release

  Liberação de uma Stock Reservation, devolvendo as unidades ao Available
  Quantity. Acontece só por ação compensatória explícita, identificada pelo
  stockReservationId. Liberar um id já liberado é no-op, não erro. Não há
  expiração automática (CONTEXT.md, ADR order/0014).

  Scenario: A liberação devolve as unidades ao Available Quantity sem mexer no Stock Balance
    Given um SKU "SKU-1" com Stock Balance 10
    And uma Stock Reservation de 4 unidades de "SKU-1"
    When a compensação libera a Stock Reservation pelo stockReservationId
    Then a Available Quantity de "SKU-1" é 10
    And o Stock Balance de "SKU-1" continua 10

  Scenario: Liberar um stockReservationId já liberado é no-op, não erro
    Given um SKU "SKU-1" com Stock Balance 10
    And uma Stock Reservation de 4 unidades de "SKU-1"
    When a compensação libera a Stock Reservation pelo stockReservationId
    And a compensação libera a Stock Reservation pelo stockReservationId
    Then a Available Quantity de "SKU-1" é 10

  Scenario: A reserva só sai por liberação explícita — liberar outro id não a libera
    Given um SKU "SKU-1" com Stock Balance 10
    And uma Stock Reservation de 4 unidades de "SKU-1"
    When a compensação libera um stockReservationId diferente
    Then a Available Quantity de "SKU-1" continua 6
