Feature: Stock Validation do Order

  A Processing Step do Order que este contexto executa: dado o conjunto de itens
  atendidos de um Order, reserva tudo ou não reserva nada. Falta de estoque
  cancela o Order; cadastro ausente não — trava em AWAITING_MASTER_DATA
  (CONTEXT.md, ADR order/0014). Quantidade parcial não existe.

  Scenario: Available Quantity é o Stock Balance menos o que está reservado
    Given um SKU "SKU-1" com Stock Balance 10
    And uma Stock Reservation de 4 unidades de "SKU-1"
    Then a Available Quantity de "SKU-1" é 6
    And o Stock Balance de "SKU-1" continua 10

  Scenario: A reserva cobre todos os itens do conjunto com uma única Stock Reservation
    Given um SKU "SKU-1" com Stock Balance 10
    And um SKU "SKU-2" com Stock Balance 5
    When o Order pede 3 unidades de "SKU-1" e 5 unidades de "SKU-2"
    Then os itens são reservados
    And existe uma única Stock Reservation cobrindo "SKU-1" com 3 e "SKU-2" com 5
    And a Available Quantity de "SKU-1" é 7
    And a Available Quantity de "SKU-2" é 0

  Scenario: Falta em um item faz o conjunto inteiro não reservar nada
    Given um SKU "SKU-1" com Stock Balance 10
    And um SKU "SKU-2" com Stock Balance 2
    When o Order pede 3 unidades de "SKU-1" e 5 unidades de "SKU-2"
    Then a resposta é Out of Stock para "SKU-2"
    And nenhuma Stock Reservation é criada
    And a Available Quantity de "SKU-1" continua 10

  Scenario: Quantidade parcial não existe — pedir 10 com 4 disponíveis é Out of Stock
    Given um SKU "SKU-1" com Stock Balance 10
    And uma Stock Reservation de 6 unidades de "SKU-1"
    When o Order pede 10 unidades de "SKU-1"
    Then a resposta é Out of Stock para "SKU-1"
    And nenhuma Stock Reservation é criada
    And a Available Quantity de "SKU-1" continua 4

  Scenario: Reserva de um Order anterior reduz o que o próximo Order encontra
    Given um SKU "SKU-1" com Stock Balance 10
    And uma Stock Reservation de 4 unidades de "SKU-1"
    When o Order pede 7 unidades de "SKU-1"
    Then a resposta é Out of Stock para "SKU-1"

  Scenario: Saldo zero com registro é Out of Stock, não ausência de registro
    Given um SKU "SKU-1" com Stock Balance 0
    When o Order pede 1 unidade de "SKU-1"
    Then a resposta é Out of Stock para "SKU-1"
    And a resposta não é Stock Record Not Found

  Scenario: SKU sem registro de estoque é Stock Record Not Found, não Out of Stock
    Given nenhum registro de estoque para "SKU-9"
    When o Order pede 1 unidade de "SKU-9"
    Then a resposta é Stock Record Not Found para "SKU-9"
    And a resposta não é Out of Stock
