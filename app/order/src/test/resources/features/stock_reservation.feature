@pending
Feature: Reserva de estoque e falta de estoque

  Como sistema
  Quero cancelar o pedido quando falta estoque, em vez de deixar o item de fora
  Para não transformar uma indisponibilidade temporária numa perda permanente de linha de pedido

  Scenario: Falta de estoque cancela o Order inteiro
    Given um Order "#123" com 3 itens atendidos e Credit Validation bem-sucedida
    And o item 2 tem Available Quantity insuficiente no MS Stock
    When a etapa "Stock Validation" executa
    Then nenhuma Stock Reservation é criada
    And o Order muda para "COMPENSATING"
    And o crédito reservado é liberado
    And o Order termina em "CANCELLED"

  Scenario: A reserva cobre só os itens atendidos
    Given um Order "#123" com 3 itens, sendo o item 2 "NOT_FULFILLED" por produto inativo
    And há Available Quantity suficiente para os itens 1 e 3
    When a etapa "Stock Validation" executa
    Then uma Stock Reservation é criada para os itens 1 e 3
    And o item 2 não participa da reserva
    And a reserva tem um único stockReservationId

  Scenario: Quantidade parcial não existe
    Given um Order "#123" cujo item 1 pede 10 unidades
    And o SKU do item 1 tem Available Quantity 4
    When a etapa "Stock Validation" executa
    Then o item 1 é tratado como Out of Stock
    And nenhuma unidade é reservada para o item 1
    And o Order não fica com o item 1 parcialmente atendido

  Scenario: Produto inativo e falta de estoque têm desfechos opostos
    Given um Order "#123" com 2 itens
    And o produto do item 1 está "INACTIVE" no MS Products
    And o SKU do item 2 tem Available Quantity suficiente
    When a Product Enrichment e a Stock Validation executam
    Then o item 1 fica "NOT_FULFILLED" e o Order segue
    And o item 2 é reservado normalmente
    And o Order não é cancelado

  Scenario: Available Quantity desconta o que está reservado
    Given um SKU com Stock Balance 10
    And 4 unidades reservadas para outros Orders
    When a Stock Validation avalia um item que pede 7 unidades
    Then a Available Quantity considerada é 6
    And o item é tratado como Out of Stock

  Scenario: Reserva não expira sozinha
    Given um Order "#123" em "COMPENSATION_FAILED" com uma Stock Reservation ativa
    When passam horas sem intervenção
    Then a Stock Reservation continua ativa
    And as unidades continuam fora da Available Quantity
    And só uma ação compensatória explícita libera a reserva

  Scenario: Liberar reserva já liberada é no-op
    Given uma Stock Reservation já liberada
    When a ação compensatória de liberar estoque é reexecutada com o mesmo stockReservationId
    Then a operação é aceita sem erro
    And nenhuma unidade é devolvida duas vezes
