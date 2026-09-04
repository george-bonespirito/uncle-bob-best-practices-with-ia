Feature: Concorrência entre as tasks de Data Enrichment no mesmo Order

  Como sistema
  Quero aplicar o resultado das tasks concorrentes de enriquecimento no mesmo Order
  Para que nenhuma task apague a mudança de outra (lost update)

  # As Processing Steps são sequenciais por pedido (ADR raiz 0008). A concorrência
  # sobre o mesmo Order vive dentro da Data Enrichment, que é um fork-join de
  # Customer Enrichment, Product Enrichment e Pricing.

  Scenario: Duas tasks concorrentes de enriquecimento não perdem a escrita uma da outra
    Given um Order "#123" criado com status "PENDING" na versão 1
    When a task "Customer Enrichment" e a task "Product Enrichment" reportam resultado para o Order concorrentemente
    Then o customer_snapshot gravado pela task "Customer Enrichment" permanece salvo
    And o product_snapshot gravado pela task "Product Enrichment" também permanece salvo
    And a versão do Order avança em 2, uma vez por escrita aplicada

  Scenario: Conflito de versão é resolvido por retry automático, não por perda de dado
    Given um Order "#123" na versão 3
    When duas escritas concorrentes tentam atualizar o Order partindo da versão 3 ao mesmo tempo
    Then uma escrita é aplicada primeiro e o Order avança para versão 4
    And a outra escrita recebe conflito de versão, recarrega o Order na versão 4 e reaplica sua própria mudança
    And nenhuma das duas mudanças é perdida
