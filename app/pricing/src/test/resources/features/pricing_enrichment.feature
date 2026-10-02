Feature: Pricing Enrichment do Order

  A Processing Step do Order que este contexto executa: dado um SKU, devolve
  Unit Price e Currency. O preço é do produto, não da relação comercial — vale
  para todos os clientes igualmente (CONTEXT.md). Price Not Found é a única
  falha de negócio, e zero é um preço válido, não ausência.

  Scenario: Enrichment devolve o Unit Price do SKU
    Given o catálogo tem o SKU "SKU-1" com Unit Price "10.00" na moeda "BRL"
    When o Order pede o preço do SKU "SKU-1"
    Then o Unit Price é "10.00"
    And a Currency é "BRL"

  Scenario: SKU sem preço falha como Price Not Found
    Given o SKU "SKU-9" não está no catálogo
    When o Order pede o preço do SKU "SKU-9"
    Then a resposta é Price Not Found
    And a razão é que ausência de preço não recusa o pedido — a task espera o cadastro

  Scenario: Preço zero é preço válido, não ausência
    Given o catálogo tem o SKU "SKU-2" com Unit Price "0.00" na moeda "BRL"
    When o Order pede o preço do SKU "SKU-2"
    Then o Unit Price é "0.00"
    And a resposta não é Price Not Found

  Scenario: Preço negativo não existe
    When alguém tenta cadastrar o Unit Price "-1.00"
    Then a criação é recusada
    And a razão é que preço inválido é recusa, não ausência

  Scenario: O preço do SKU não depende do cliente
    Given o catálogo tem o SKU "SKU-1" com Unit Price "10.00" na moeda "BRL"
    When o Order pede o preço do SKU "SKU-1"
    Then o Unit Price é "10.00"
    And a razão é que a assinatura do enriquecimento não recebe cliente
