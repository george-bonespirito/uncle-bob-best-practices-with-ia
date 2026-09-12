@pending
Feature: Crédito do cliente e ciclo de vida do hold

  Como sistema
  Quero comprometer crédito antes de consumir estoque e convertê-lo quando faturar
  Para que o limite reflita o que foi efetivamente vendido, sem travar pedido esperando pagamento

  Scenario: Crédito suficiente cria hold e o Order segue
    Given um Order "#123" com Order Total 500,00
    And o Customer tem Available Credit 800,00 no MS Contabil
    When a etapa "Credit Validation" executa
    Then o creditStatus é "APPROVED"
    And um creditHoldId é gravado no Order
    And o Available Credit do Customer passa a 300,00
    And o Order segue para "Stock Validation"

  Scenario: Crédito insuficiente cancela o Order
    Given um Order "#123" com Order Total 500,00
    And o Customer tem Available Credit 200,00 no MS Contabil
    When a etapa "Credit Validation" executa
    Then o creditStatus é "REJECTED"
    And o creditHoldId vem nulo
    And o Order muda para "CANCELLED"
    And nenhum estoque chega a ser reservado

  Scenario: Cliente sem conta de crédito espera, não cancela
    Given um Order "#123" em "PROCESSING"
    And o Customer não tem cadastro de crédito no MS Contabil
    When a etapa "Credit Validation" reporta Credit Account Not Found
    Then o Order muda para "AWAITING_MASTER_DATA"
    And o Order não é cancelado
    And nenhum Credit Hold é criado

  Scenario: Available Credit zero é recusa, não cadastro ausente
    Given um Order "#123" com Order Total 100,00
    And o Customer tem cadastro de crédito com Available Credit 0,00
    When a etapa "Credit Validation" executa
    Then o creditStatus é "REJECTED"
    And o Order muda para "CANCELLED"
    And o Order não vai para "AWAITING_MASTER_DATA"

  Scenario: Faturamento converte o hold em dívida
    Given um Order "#123" com um Credit Hold ativo de 500,00
    When a etapa "Invoice Generation" emite a nota
    Then o Credit Hold é convertido em dívida
    And o valor continua fora do Available Credit
    And o hold deixa de ser reversível por compensação

  Scenario: Order cancelado antes de faturar libera o hold
    Given um Order "#123" com um Credit Hold ativo de 500,00
    And a etapa "Stock Validation" falha permanentemente
    When a task compensatória "release-credit-hold" executa
    Then o valor volta para o Available Credit do Customer
    And o Order termina em "CANCELLED"

  Scenario: Hold não expira sozinho
    Given um Order "#123" em "COMPENSATION_FAILED" com um Credit Hold ativo
    When passam horas sem intervenção
    Then o Credit Hold continua ativo
    And o valor continua fora do Available Credit
    And só uma ação compensatória explícita libera o hold

  Scenario: Liberar hold já liberado é no-op
    Given um Credit Hold já liberado
    When a task compensatória "release-credit-hold" é reexecutada com o mesmo creditHoldId
    Then a operação é aceita sem erro
    And nenhum valor é devolvido duas vezes
