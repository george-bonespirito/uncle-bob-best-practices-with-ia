# Notifications (Aviso ao Cliente)

Contexto dono do aviso ao cliente sobre o andamento do pedido. Consome o tópico CDC do Order e envia WhatsApp quando o status muda de forma que interessa a quem comprou.

**Não faz parte da Saga.** Nenhuma falha aqui afeta o Order: não há compensação para "avisei e depois desavisei".

## Language

**Notification**:
Uma mensagem enviada ao cliente por causa de uma transição de status do Order. Não é entidade persistida do domínio — é um efeito, disparado por evento e registrado só para diagnóstico.
_Avoid_: alerta, aviso (sem qualificar), mensagem (colide com mensagem Kafka), comunicado

**Notifiable Status**:
Os status do Order que geram Notification: `PROCESSING`, `CANCELLED` e `COMPLETED`. Os demais **não notificam** — `PENDING` é estado inicial e não é transição; `INVOICING`, `COMPENSATING` e `COMPENSATION_FAILED` são internos e não informam nada ao cliente; `AWAITING_MASTER_DATA` é lacuna de cadastro nossa, sobre a qual o cliente não pode agir.
_Avoid_: todos os status, mudança de status (sem qualificar quais), evento de status

**Status Transition**:
A passagem de um status para outro, que é o que dispara a Notification. Criação de Order não é transição, e re-snapshot do Debezium (`op: r`) também não — os dois já estão cobertos por cenário em `status_change_notification.feature`.
_Avoid_: atualização do Order (qualquer campo muda o registro; só status importa aqui), evento CDC (é o transporte, não o conceito)

**Notification Key**:
`(order_id, novo_status)` — a chave de idempotência. O CDC é at-least-once, então o mesmo evento pode chegar duas vezes; a chave garante **uma mensagem por transição**. Mensagem duplicada ao cliente é dano visível e irreversível, diferente de processamento duplicado interno.
_Avoid_: deduplicação por offset (o offset muda no reprocessamento), id do evento

**Message Template**:
Modelo pré-aprovado pela Meta, exigência do canal e não escolha nossa ([ADR notifications/0002](docs/adr/0002-meta-cloud-api-for-whatsapp.md)). Carrega **número do pedido e status, e nada além disso**.
_Avoid_: mensagem livre, texto customizado, corpo dinâmico

**Partial Completion Template**:
Variante do template de `COMPLETED`, usada quando o pedido terminou com itens não atendidos: diz que houve item não atendido, **sem dizer qual** ([ADR notifications/0005](docs/adr/0005-partial-completion-template-selection.md)). É escolha de template dentro da mesma Status Transition — conclusão parcial gera **uma** Notification, e a Notification Key não muda.
_Avoid_: notificação de item, segunda mensagem, status `COMPLETED_PARTIAL` (não existe — o pedido está `COMPLETED` como qualquer outro)

**Partial Fulfillment Flag**:
`partially_fulfilled`, coluna da linha de `orders` escrita pela Invoice Generation na transição terminal, e o único dado pelo qual este contexto sabe que deve usar o Partial Completion Template. `fulfillment_status` é de `order_items`, tabela que este contexto **não consome** ([ADR notifications/0005](docs/adr/0005-partial-completion-template-selection.md)).
_Avoid_: consultar o Order no envio, ler `order_items`, contar itens

**Undeliverable Notification**:
Envio que falhou definitivamente — Meta fora do ar após retry, ou número inválido. Vai para DLT e **não afeta o Order**. Nunca é descartada em silêncio: cliente que não foi avisado é problema de operação, não não-evento.
_Avoid_: falha de notificação como falha de pedido, notificação perdida, erro ignorado

**Contact Lookup**:
Consulta síncrona ao [Customers](../customers/CONTEXT.md) no momento do envio, para obter o WhatsApp Phone atual — nunca do Snapshot do Order, para que troca de número valha imediatamente ([ADR notifications/0003](docs/adr/0003-lookup-contact-at-send-time.md)).
_Avoid_: telefone do snapshot, contato do pedido, dado congelado

**Delivery Receipt**:
Não existe. O sistema sabe que entregou a mensagem à Meta; não sabe se o cliente leu, nem se o aparelho recebeu. Confirmação de leitura não é consumida.
_Avoid_: confirmação de leitura, ack do cliente, status da mensagem, visto

**Notification Preference**:
Não existe. Não há opt-out, canal alternativo, idioma nem janela de horário. Todo Customer com WhatsApp Phone recebe todas as Notifications dos Notifiable Status.
_Avoid_: preferência, opt-in, opt-out, canal preferido, e-mail, SMS
