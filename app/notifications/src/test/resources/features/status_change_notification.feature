@pending
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

  Scenario: Status interno não notifica o cliente
    Given um Order "#123" em "PROCESSING"
    When o status muda para "COMPENSATING"
    Then nenhuma mensagem WhatsApp é enviada
    And a razão é que "COMPENSATING" não é Notifiable Status

  Scenario: AWAITING_MASTER_DATA não notifica
    Given um Order "#123" em "PROCESSING"
    When o status muda para "AWAITING_MASTER_DATA" por cadastro faltando
    Then nenhuma mensagem WhatsApp é enviada
    And a razão é que a lacuna é de cadastro nosso, e o cliente não pode agir sobre ela

  Scenario: Evento CDC repetido não gera segunda mensagem
    Given um Order "#123" cuja transição para "COMPLETED" já foi notificada
    When o mesmo evento CDC chega novamente após reprocessamento do tópico
    Then nenhuma mensagem adicional é enviada
    And a deduplicação usa a chave (order_id, novo_status)
    And a razão é que o offset muda no reprocessamento e não serve como chave

  Scenario: Falha de envio não afeta o Order
    Given um Order "#123" que mudou para "COMPLETED"
    And a Meta Cloud API está indisponível
    When o envio falha após esgotar as tentativas
    Then a notificação vai para o DLT
    And o Order permanece em "COMPLETED"
    And nenhuma ação compensatória é disparada no Order

  Scenario: Template não carrega dado do cliente nem do item
    Given um Order "#123" com 3 itens e customer_snapshot preenchido
    When a mensagem de mudança para "PROCESSING" é montada
    Then o template contém o número do pedido e o status
    And não contém nome, documento, endereço nem valores
