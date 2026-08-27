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
