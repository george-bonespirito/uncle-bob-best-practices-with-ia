Feature: Customer Enrichment do Order

  A Processing Step do Order que este contexto executa: dado um customer_id,
  devolve Name, Document e Default Delivery Address como Snapshot. É a única
  coisa que o contexto faz pelo Order, e Customer Not Found é a única falha
  de negócio (CONTEXT.md, ADR order/0011).

  Scenario: Enrichment devolve Name, Document e Default Delivery Address como Snapshot
    Given um Customer cadastrado "Distribuidora Horizonte" com documento "11222333000181"
    When o Order pede o enriquecimento do customer_id
    Then o Snapshot traz o Name "Distribuidora Horizonte"
    And o Snapshot traz o Document "11222333000181"
    And o Snapshot traz o Default Delivery Address

  Scenario: customer_id inexistente falha como Customer Not Found — a única falha de negócio
    Given um customer_id que não existe no cadastro
    When o Order pede o enriquecimento
    Then a resposta é Customer Not Found
