@pending
Feature: Atendimento parcial quando o produto está inativo

  Como sistema
  Quero deixar de fora só o item cujo produto saiu de linha, em vez de recusar o pedido inteiro
  Para não descartar uma venda válida por uma decisão de catálogo que o cliente não controla

  Scenario: Item com produto INACTIVE não é atendido e o Order segue
    Given um Order "#123" em "PROCESSING" com 3 itens
    And o produto do item 2 está "INACTIVE" no MS Products
    When a task "Product Enrichment" reporta o resultado
    Then o item 2 fica com fulfillment_status "NOT_FULFILLED"
    And o motivo do item 2 é "PRODUCT_INACTIVE"
    And os itens 1 e 3 ficam com fulfillment_status "FULFILLED"
    And o Order segue para "Credit Validation"
    And o Order não é cancelado

  Scenario: As etapas seguintes operam só sobre os itens atendidos
    Given um Order "#123" com 3 itens, sendo o item 2 "NOT_FULFILLED"
    When Credit Validation, Stock Validation e Route Calculation executam
    Then o valor submetido à Credit Validation não inclui o item 2
    And nenhuma reserva de estoque é feita para o item 2
    And o cálculo de carga do Route não inclui o peso nem as dimensões do item 2

  Scenario: Order sem nenhum item atendido é cancelado
    Given um Order "#123" em "PROCESSING" com 2 itens
    And os produtos dos dois itens estão "INACTIVE" no MS Products
    When a task "Product Enrichment" reporta o resultado
    Then nenhum item fica "FULFILLED"
    And o Order muda para "CANCELLED"
    And o Order não segue para "Credit Validation"

  Scenario: Produto desativado depois do enriquecimento é pego no faturamento
    Given um Order "#123" com o item 1 "FULFILLED" e as 4 etapas hard-blocking concluídas
    And o produto do item 1 passou a "INACTIVE" depois da Product Enrichment
    When a task "Invoice Generation" reconfere o Product Status via Products Lookup
    Then o item 1 fica com fulfillment_status "NOT_FULFILLED"
    And o motivo do item 1 é "PRODUCT_INACTIVE"
    And a fatura não inclui o item 1

  Scenario: Produto desativado depois de faturado não altera o Order
    Given um Order "#123" já faturado, com o item 1 "FULFILLED"
    When o produto do item 1 passa a "INACTIVE" no MS Products
    Then o item 1 continua "FULFILLED"
    And a fatura emitida não muda
    And o product_snapshot do item 1 preserva o Name e a Category do momento do enriquecimento

  Scenario: Produto inexistente e produto inativo têm desfechos opostos
    Given um Order "#123" em "PROCESSING" com 2 itens
    And o produto do item 1 não existe no MS Products
    And o produto do item 2 está "INACTIVE" no MS Products
    When a task "Product Enrichment" reporta o resultado
    Then o Order muda para "AWAITING_MASTER_DATA" por causa do item 1
    And o item 2 fica com fulfillment_status "NOT_FULFILLED"
    And o Order não é cancelado

  Scenario: Estoque do item que cai no faturamento é liberado, não vaza
    Given um Order "#123" com 3 itens reservados e Credit Hold ativo
    And o produto do item 3 passou a "INACTIVE" depois da Stock Validation
    When a etapa "Invoice Generation" reconfere o Product Status
    Then a task compensatória "release-stock-reservation" libera só o item 3
    And a Stock Reservation continua ativa para os itens 1 e 2
    And o stockReservationId permanece o mesmo
    And as unidades do item 3 voltam para a Available Quantity

  Scenario: Crédito excedente é liberado na conversão
    Given um Order "#123" com Credit Hold de 500,00
    And o item 3, de 100,00, virou "NOT_FULFILLED" no faturamento
    When a Hold Conversion executa
    Then 400,00 são convertidos em dívida
    And 100,00 voltam para o Available Credit do Customer
    And nenhum hold permanece ativo para esse Order

  Scenario: Rota não é refeita, e isso é deliberado
    Given um Order "#123" cuja rota foi calculada com 3 itens
    And o item 3 virou "NOT_FULFILLED" no faturamento
    When a etapa "Invoice Generation" conclui
    Then o routeId e o estimatedDelivery permanecem os mesmos
    And nenhuma liberação de rota acontece
    And a razão é que carga superdimensionada não prende recurso de terceiros

  Scenario: Cliente é avisado do atendimento parcial, sem detalhe de item
    Given um Order "#123" faturado com o item 2 "NOT_FULFILLED"
    When o Order muda para "COMPLETED"
    Then a mensagem enviada usa o template variante de conclusão parcial
    And a mensagem não lista quais itens ficaram de fora
    And nenhuma notificação adicional por item é enviada

  Scenario: Todos os itens caem no faturamento e o Order compensa
    Given um Order "#123" com 2 itens, Credit Hold ativo e Stock Reservation ativa
    And os produtos dos dois itens passaram a "INACTIVE"
    When a etapa "Invoice Generation" reconfere o Product Status
    Then nenhuma Invoice é emitida
    And o Order muda para "COMPENSATING"
    And crédito e estoque são liberados integralmente
    And o Order termina em "CANCELLED"

  Scenario: Liberação parcial é idempotente
    Given um Order "#123" cujo item 3 já teve o estoque liberado
    When a task "release-stock-reservation" é reexecutada para o item 3
    Then a operação é aceita sem erro
    And nenhuma unidade é devolvida duas vezes
