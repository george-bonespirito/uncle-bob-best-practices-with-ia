Feature: Importação em massa de pedidos via planilha

  Como usuário do sistema
  Quero subir uma planilha com múltiplos pedidos de uma vez
  Para não precisar cadastrar cada Order manualmente

  Scenario: Usuário importa uma planilha válida de pedidos
    Given uma planilha .csv com 3 linhas de pedidos válidas
    When o usuário faz upload da planilha para a API de importação de Orders
    Then os 3 Orders são criados com status "PENDING"
    And um workflow de processamento é disparado no Orkes para cada Order criado

  Scenario: Planilha com uma linha inválida não bloqueia as demais
    Given uma planilha .csv com 3 linhas, sendo 1 com dado de cliente ausente
    When o usuário faz upload da planilha para a API de importação de Orders
    Then 2 Orders são criados com status "PENDING"
    And a linha inválida é reportada no resultado da importação com o motivo da falha
