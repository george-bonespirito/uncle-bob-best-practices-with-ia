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
