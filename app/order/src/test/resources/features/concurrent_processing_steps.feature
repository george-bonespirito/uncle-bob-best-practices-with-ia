Feature: Concorrência entre Processing Steps no mesmo Order

  Como sistema
  Quero aplicar o resultado de múltiplas Processing Steps concorrentes no mesmo Order
  Para que nenhuma etapa apague a mudança de outra (lost update)

  Scenario: Duas Processing Steps concorrentes não perdem a escrita uma da outra
    Given um Order "#123" criado com status "PENDING" na versão 1
    When a etapa "Data Enrichment" e a etapa "Credit Validation" reportam resultado para o Order concorrentemente
    Then o campo alterado pela etapa "Data Enrichment" permanece salvo
    And o campo alterado pela etapa "Credit Validation" também permanece salvo
    And a versão do Order avança em 2, uma vez por escrita aplicada

  Scenario: Conflito de versão é resolvido por retry automático, não por perda de dado
    Given um Order "#123" na versão 3
    When duas escritas concorrentes tentam atualizar o Order partindo da versão 3 ao mesmo tempo
    Then uma escrita é aplicada primeiro e o Order avança para versão 4
    And a outra escrita recebe conflito de versão, recarrega o Order na versão 4 e reaplica sua própria mudança
    And nenhuma das duas mudanças é perdida
