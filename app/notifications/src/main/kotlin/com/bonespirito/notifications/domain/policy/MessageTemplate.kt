package com.bonespirito.notifications.domain.policy

/**
 * Template pré-aprovado pela Meta, carregando número do pedido e status,
 * e nada além disso (ADR notifications/0004). O COMPLETED tem variante:
 * conclusão parcial usa Partial Completion Template quando o Order carrega
 * partially_fulfilled (ADR notifications/0005) — escolha de template dentro
 * da mesma transição, sem mudar a Notification Key.
 */
enum class MessageTemplate(val statusLabel: String) {
    PROCESSING("PROCESSING"),
    CANCELLED("CANCELLED"),
    COMPLETED("COMPLETED"),
    PARTIAL_COMPLETION("COMPLETED"),
    ;

    companion object {
        fun forStatus(status: NotifiableStatus, partiallyFulfilled: Boolean): MessageTemplate =
            when (status) {
                NotifiableStatus.PROCESSING -> PROCESSING
                NotifiableStatus.CANCELLED -> CANCELLED
                NotifiableStatus.COMPLETED ->
                    if (partiallyFulfilled) PARTIAL_COMPLETION else COMPLETED
            }
    }
}

/**
 * Monta o corpo da mensagem: número do pedido e status, nada mais.
 * A variante de conclusão parcial acrescenta o fato de haver item não
 * atendido, sem dizer qual.
 */
fun renderMessage(template: MessageTemplate, orderId: String): String =
    when (template) {
        MessageTemplate.PARTIAL_COMPLETION -> "Pedido $orderId: status ${template.statusLabel}, com item não atendido"
        else -> "Pedido $orderId: status ${template.statusLabel}"
    }
