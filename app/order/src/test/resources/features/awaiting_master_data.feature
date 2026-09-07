@pending
Feature: Order espera cadastro quando cliente ou produto não existe

  Como sistema
  Quero segurar o pedido até o dado de cadastro existir, em vez de cancelá-lo
  Para não perder uma venda válida só porque alguém não cadastrou o cliente ou o produto

  Scenario: Cliente inexistente trava a Data Enrichment sem cancelar o pedido
    Given um Order "#123" em "PROCESSING" com um customer_id que não existe no MS Customers
    When a task "Customer Enrichment" reporta Customer Not Found
    Then o Order muda para "AWAITING_MASTER_DATA"
    And o Order não é cancelado
    And nenhuma ação compensatória é disparada

  Scenario: Nenhuma reserva fica presa enquanto o Order espera
    Given um Order "#123" em "AWAITING_MASTER_DATA"
    Then nenhum crédito está reservado no MS Contabil para esse Order
    And nenhum estoque está reservado no MS Stock para esse Order
    And a razão é que Credit Validation e Stock Validation vêm depois da Data Enrichment na sequência

  Scenario: Cadastrar o dado e reexecutar a task faz o Order avançar
    Given um Order "#123" em "AWAITING_MASTER_DATA" por cliente inexistente
    When o cliente é cadastrado no MS Customers
    And a task "Customer Enrichment" é reexecutada
    Then a task reporta sucesso e o customer_snapshot é gravado no Order
    And o Order volta para "PROCESSING"
    And a etapa "Credit Validation" é disparada em seguida

  Scenario: Só a task que falhou é reexecutada
    Given um Order "#123" em "AWAITING_MASTER_DATA" por produto inexistente
    And as tasks "Customer Enrichment" e "Pricing" já reportaram sucesso
    When o produto é cadastrado e a task "Product Enrichment" é reexecutada
    Then as tasks "Customer Enrichment" e "Pricing" não são reexecutadas
    And o resultado já aplicado dessas tasks permanece salvo no Order

  Scenario: O Order espera indefinidamente, sem prazo para cancelar
    Given um Order "#123" em "AWAITING_MASTER_DATA"
    When passa um período longo sem ninguém cadastrar o dado faltante
    Then o Order permanece em "AWAITING_MASTER_DATA"
    And o Order não é cancelado automaticamente
