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
    And a task compensatória "release-credit-hold" libera o crédito reservado
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

  Scenario: Atendimento parcial já aplicado não salva o Order de falta de estoque
    Given um Order "#123" com 3 itens
    And o item 1 está "NOT_FULFILLED" por produto "INACTIVE"
    And os itens 2 e 3 estão "FULFILLED"
    And o item 3 tem Available Quantity insuficiente no MS Stock
    When a etapa "Stock Validation" executa
    Then nenhuma Stock Reservation é criada
    And o Order muda para "CANCELLED"
    And o item 1 continua "NOT_FULFILLED" com motivo "PRODUCT_INACTIVE"
    And a compensação cobre só o que chegou a ser reservado

  Scenario: SKU sem registro de estoque espera, não cancela
    Given um Order "#123" em "PROCESSING"
    And o SKU do item 1 não tem registro de estoque no MS Stock
    When a etapa "Stock Validation" reporta Stock Record Not Found
    Then o Order muda para "AWAITING_MASTER_DATA"
    And o Order não é cancelado
    And nenhuma ação compensatória é disparada

  Scenario: Resultado da Stock Validation na falha não traz reserva
    Given um Order "#123" cujo item 1 está Out of Stock
    When o MS Stock publica em "order.stock-validation.result"
    Then a mensagem indica falha
    And o stockReservationId vem nulo
    And reservedItems vem vazio

  Scenario: Resultado da Stock Validation no sucesso lista o que foi reservado
    Given um Order "#123" com os itens 1 e 3 "FULFILLED" e estoque suficiente
    When o MS Stock publica em "order.stock-validation.result"
    Then o stockReservationId vem preenchido
    And reservedItems lista os itens 1 e 3
    And reservedItems não inclui item "NOT_FULFILLED"

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
    When a task compensatória "release-stock-reservation" é reexecutada com o mesmo stockReservationId
    Then a operação é aceita sem erro
    And nenhuma unidade é devolvida duas vezes
