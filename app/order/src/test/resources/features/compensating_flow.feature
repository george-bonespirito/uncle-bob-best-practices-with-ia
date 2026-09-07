@pending
Feature: Fluxo compensatório quando uma Processing Step falha

  Como sistema
  Quero desfazer o efeito colateral de etapas que já tiveram sucesso
  Para não deixar estoque ou crédito reservados num pedido cancelado

  Scenario: Stock Validation falha depois de Credit Validation já ter sucesso
    Given um Order "#123" em "PROCESSING"
    And a etapa "Credit Validation" já reportou sucesso, com crédito reservado no MS Contabil
    When a etapa "Stock Validation" falha permanentemente por falta de estoque
    Then o Order muda para "COMPENSATING"
    And o MS Contabil recebe uma ação compensatória para liberar o crédito reservado
    And o Order muda para "CANCELLED" após a compensação ser confirmada

  Scenario: Enrichment falha mesmo sem efeito colateral, e ainda assim cancela o pedido
    Given um Order "#123" em "PROCESSING"
    And a etapa "Data Enrichment" é a primeira da sequência, então nenhuma etapa com efeito colateral rodou ainda
    When a etapa "Data Enrichment" falha permanentemente por um motivo que não seja cadastro ausente
    Then o Order muda direto para "CANCELLED", sem passar por "COMPENSATING"
    And nenhuma ação compensatória é disparada

  Scenario: Ação compensatória falha e exige intervenção manual
    Given um Order "#123" em "COMPENSATING", com Stock Validation pendente de compensação
    When a ação compensatória de liberar estoque falha após esgotar as tentativas
    Then o Order muda para "COMPENSATION_FAILED"
    And o Order não volta a tentar a compensação automaticamente

  Scenario: Invoice Generation só roda depois das 4 etapas hard-blocking terem sucesso
    Given um Order "#123" com Enrichment, Credit Validation, Stock Validation e Route Calculation concluídas com sucesso
    When o Orkes avalia o workflow
    Then a etapa "Invoice Generation" é disparada
    And o Order muda para "INVOICING"
