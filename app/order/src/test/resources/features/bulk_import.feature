Feature: Importação em massa de pedidos via planilha

  Como usuário do sistema
  Quero subir uma planilha denormalizada com múltiplos pedidos de uma vez
  Para não precisar cadastrar cada Order manualmente

  Scenario: Upload é aceito e processado em background
    Given uma planilha .csv com linhas de 3 pedidos válidos (cabeçalho repetido por linha de item)
    When o usuário faz upload da planilha para "POST /imports/orders"
    Then a API responde "202 Accepted" com um importBatchId e status "PENDING"
    And o processamento roda em background, sem o cliente esperar a resposta

  Scenario: Consulta de status reflete o resultado após o processamento
    Given um importBatchId de uma planilha com 3 pedidos válidos, já processada
    When o usuário consulta "GET /imports/orders/{importBatchId}"
    Then o status é "COMPLETED"
    And "ordersCreated" é 3
    And um workflow de processamento é disparado no Orkes para cada Order criado

  Scenario: Linha inválida não bloqueia as demais, aparece no status final
    Given uma planilha .csv com linhas de 3 pedidos, sendo 1 com dado de cliente ausente
    When o usuário faz upload e aguarda o processamento terminar
    Then o status final é "COMPLETED_WITH_ERRORS"
    And "ordersCreated" é 2
    And a linha inválida aparece em import_batch_row_errors com o motivo da falha

  Scenario: Reenvio do mesmo arquivo é recusado no upload
    Given uma planilha .csv já importada com sucesso
    When o usuário faz upload do mesmo arquivo novamente para "POST /imports/orders"
    Then a API responde "409 Conflict" com o importBatchId do lote original
    And nenhum Import Batch novo é criado

  Scenario: Linha com customer_id inexistente é recusada sem bloquear o lote
    Given uma planilha .csv com linhas de 3 pedidos, sendo 1 com um customer_id que não existe no MS Customers
    When o usuário faz upload e aguarda o processamento terminar
    Then o status final é "COMPLETED_WITH_ERRORS"
    And "ordersCreated" é 2
    And a linha do customer_id inexistente aparece em import_batch_row_errors

  Scenario: MS Customers indisponível não recusa pedido nenhum
    Given uma planilha .csv com linhas de 3 pedidos válidos
    And o MS Customers está indisponível durante o import
    When o usuário faz upload e aguarda o processamento terminar
    Then "ordersCreated" é 3
    And nenhuma linha aparece em import_batch_row_errors por causa da indisponibilidade
    And a validação do cliente acontece na Processing Step de Data Enrichment

  Scenario: Pedido duplicado (mesma origem + mesmo número) não gera Order duplicado
    Given um Order já importado com source_system_code "ERP-SAP" e source_order_number "PO-123"
    When uma nova planilha traz uma linha com o mesmo source_system_code e source_order_number
    Then essa linha é reportada em import_batch_row_errors como duplicata
    And nenhum Order novo é criado para essa linha
