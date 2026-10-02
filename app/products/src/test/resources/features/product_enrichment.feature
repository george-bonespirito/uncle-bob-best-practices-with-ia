Feature: Product Enrichment do Order

  A Processing Step do Order que este contexto executa: dado um sku, devolve
  Name, Category e Product Status como Snapshot (CONTEXT.md). Description e
  Physical Attributes ficam aqui. Product Not Found é o sku não existir, e
  INACTIVE é produto que existe e não é vendável — desfechos opostos.

  Scenario: Enrichment devolve Name, Category e Product Status
    Given o Product "SKU-1" "Furadeira de Impacto" categoria "Ferramentas" com atributos físicos cadastrados
    When o Order pede o enriquecimento do sku "SKU-1"
    Then o Snapshot traz o Name "Furadeira de Impacto"
    And o Snapshot traz a Category "Ferramentas"
    And o Snapshot traz o Product Status "ACTIVE"

  Scenario: Description não vai pro Snapshot — a linha de pedido mostra Name
    Given o Product "SKU-1" "Furadeira de Impacto" com Description "Furadeira 750W com maleta" e atributos físicos cadastrados
    When o Order pede o enriquecimento do sku "SKU-1"
    Then o Snapshot não contém a Description

  Scenario: Physical Attributes são obrigatórios no cadastro
    When alguém tenta cadastrar um Product sem Physical Attributes
    Then a criação é recusada
    And a razão é que Product sem eles é inútil pro Route

  Scenario: Physical Attributes não vão pro Snapshot — o Order não roteiriza
    Given o Product "SKU-1" "Furadeira de Impacto" categoria "Ferramentas" com atributos físicos cadastrados
    When o Order pede o enriquecimento do sku "SKU-1"
    Then o Snapshot não contém peso nem dimensões

  Scenario: Product INACTIVE existe e enriquece — não é Product Not Found
    Given o Product "SKU-2" "Serra Circular" categoria "Ferramentas" com atributos físicos cadastrados e status "INACTIVE"
    When o Order pede o enriquecimento do sku "SKU-2"
    Then o Snapshot traz o Product Status "INACTIVE"
    And a resposta não é Product Not Found

  Scenario: sku inexistente falha como Product Not Found
    Given o sku "SKU-9" não existe no cadastro
    When o Order pede o enriquecimento do sku "SKU-9"
    Then a resposta é Product Not Found
    And a razão é que a task espera o cadastro, não recusa o pedido
