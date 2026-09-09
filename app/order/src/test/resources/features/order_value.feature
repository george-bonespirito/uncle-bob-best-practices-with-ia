@pending
Feature: Valor do Order — total derivado e preço congelado

  Como sistema
  Quero somar o total a partir dos itens atendidos e preservar o preço acordado
  Para que o valor cobrado corresponda ao que foi efetivamente vendido, sem reprecificar pedido em curso

  Scenario: Total do Order é a soma dos itens atendidos
    Given um Order "#123" com 3 itens precificados
    And o item 2 está "NOT_FULFILLED"
    When o total do Order é calculado
    Then o total considera apenas os itens 1 e 3
    And nenhuma coluna de total é gravada na tabela "orders"

  Scenario: Item que deixa de ser atendido depois muda o total sem recálculo explícito
    Given um Order "#123" com 3 itens atendidos e precificados
    When a Invoice Generation marca o item 3 como "NOT_FULFILLED"
    Then o total do Order passa a considerar apenas os itens 1 e 2
    And nenhum passo de recálculo precisou ser disparado

  Scenario: Preço não é reconferido antes de faturar
    Given um Order "#123" com o item 1 precificado em 10,00
    And o Unit Price do SKU do item 1 mudou para 12,00 no MS Pricing
    When a Invoice Generation executa
    Then o unit_price do item 1 continua 10,00
    And a fatura usa 10,00
    And nenhuma consulta de preço é feita ao MS Pricing

  Scenario: SKU sem preço trava o Order, como cliente e produto inexistentes
    Given um Order "#123" em "PROCESSING" com um SKU sem preço cadastrado no MS Pricing
    When a task "Pricing Enrichment" reporta Price Not Found
    Then o Order muda para "AWAITING_MASTER_DATA"
    And o Order não é cancelado
    And nenhuma ação compensatória é disparada

  Scenario: Preço zero é preço, não ausência de preço
    Given um Order "#123" com um SKU cujo Unit Price cadastrado é 0,00
    When a task "Pricing Enrichment" reporta o resultado
    Then o item é precificado com unit_price 0,00
    And o Order não vai para "AWAITING_MASTER_DATA"
