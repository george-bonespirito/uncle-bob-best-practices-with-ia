@pending
Feature: Emissão da Invoice e congelamento do pedido

  Como sistema
  Quero emitir o documento de faturamento só depois de reconferir o que ainda é vendável
  Para que o pedido pare de mudar exatamente quando deixa de ser reversível

  Scenario: Emissão bem-sucedida fecha o ciclo
    Given um Order "#123" com as 4 etapas hard-blocking concluídas
    And todos os itens continuam "ACTIVE" no MS Products
    When a etapa "Invoice Generation" executa
    Then um invoiceNumber é gravado no Order
    And o invoice_issued_at é preenchido
    And o Credit Hold é convertido em dívida
    And o Order termina em "COMPLETED"

  Scenario: Falha permanente na emissão cancela e compensa os dois
    Given um Order "#123" com Credit Hold ativo e Stock Reservation ativa
    When a etapa "Invoice Generation" falha permanentemente
    Then o Order muda para "COMPENSATING"
    And a task compensatória "release-credit-hold" libera o crédito
    And a task compensatória "release-stock-reservation" libera o estoque
    And o Order termina em "CANCELLED"
    And nenhum invoiceNumber é gravado

  Scenario: Hold não convertido pode ser liberado normalmente
    Given um Order "#123" cuja Invoice Generation falhou antes de emitir
    When a task compensatória "release-credit-hold" executa
    Then o valor volta para o Available Credit do Customer
    And nenhuma conversão em dívida chega a acontecer

  Scenario: A fatura cobre só os itens atendidos
    Given um Order "#123" com 3 itens, sendo o item 2 "NOT_FULFILLED"
    When a etapa "Invoice Generation" emite a nota
    Then a Invoice lista apenas os itens 1 e 3
    And o valor faturado é o Order Total daquele momento
    And o item 2 permanece registrado no Order como "NOT_FULFILLED"

  Scenario: Numeração aceita buraco quando um Order é cancelado
    Given uma Invoice emitida com invoiceNumber 1001
    And um Order "#124" que recebeu o invoiceNumber 1002 e foi cancelado em seguida
    When o próximo Order é faturado
    Then ele recebe o invoiceNumber 1003
    And o número 1002 não é reaproveitado
    And a lacuna não é tratada como erro

  Scenario: Invoice emitida é terminal
    Given um Order "#123" já faturado
    When alguém tenta cancelar ou reemitir a Invoice
    Then a operação não é suportada pelo sistema
    And o invoiceNumber permanece o mesmo
