Feature: Notificação ao cliente a cada mudança de status do pedido

  Como cliente que fez um pedido
  Quero ser avisado via WhatsApp quando o status do meu pedido mudar
  Para acompanhar o andamento sem precisar consultar o sistema

  Scenario: Cliente recebe WhatsApp quando o status do Order muda
    Given um Order "#123" com status "PENDING" e cliente com WhatsApp cadastrado
    When o status do Order muda para "PROCESSING"
    Then um evento CDC do Order é publicado no tópico Kafka
    And MS Notifications envia uma mensagem WhatsApp ao cliente dono do Order

  Scenario: Mudança em outro campo do Order não dispara notificação
    Given um Order "#123" com status "PROCESSING"
    When um campo diferente de status é alterado no Order, sem mudança de status
    Then nenhuma mensagem WhatsApp é enviada

  Scenario: Re-snapshot do Debezium não dispara notificação nenhuma
    Given 500 Orders existentes no banco, em diversos status
    And o slot de replicação foi invalidado e o Debezium refez o snapshot
    When os 500 eventos de snapshot chegam no tópico CDC, com op "r"
    Then nenhuma mensagem WhatsApp é enviada
    And a razão é que evento de snapshot não é mudança de status

  Scenario: Criação de Order não dispara notificação
    Given um Order novo criado pelo import, com status "PENDING"
    When o evento CDC de insert chega no tópico, com op "c"
    Then nenhuma mensagem WhatsApp é enviada
    And a razão é que "PENDING" é o estado inicial, não uma mudança de status

  Scenario: Cliente é notificado no número atual, não no do momento do pedido
    Given um Order "#123" cujo cliente trocou de número de WhatsApp depois do pedido criado
    When o status do Order muda para "PROCESSING"
    Then MS Notifications consulta o MS Customers e obtém o número atual
    And a mensagem é enviada para o número atual
