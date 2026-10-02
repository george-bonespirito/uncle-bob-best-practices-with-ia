package com.bonespirito.notifications.domain.policy

sealed interface NotificationDecision {
    data class Notify(
        val key: NotificationKey,
        val template: MessageTemplate,
    ) : NotificationDecision

    data class Ignore(val reason: String) : NotificationDecision
}

/**
 * Decide se um evento CDC da tabela `orders` gera Notification.
 * Só gera quando é transição real para Notifiable Status: op "u"
 * (update — descartando snapshot "r" e insert "c", ADR notifications/0001)
 * e before.status != after.status.
 */
fun decideNotification(
    orderId: String,
    op: String,
    beforeStatus: String,
    afterStatus: String,
    partiallyFulfilled: Boolean,
): NotificationDecision =
    when {
        op != "u" -> NotificationDecision.Ignore(notAnUpdateReason(op))
        beforeStatus == afterStatus -> NotificationDecision.Ignore("mudança em outro campo não é mudança de status")
        else -> {
            val status = NotifiableStatus.fromOrderStatus(afterStatus)
            if (status == null) {
                NotificationDecision.Ignore(nonNotifiableReason(afterStatus))
            } else {
                NotificationDecision.Notify(
                    key = NotificationKey(orderId, status),
                    template = MessageTemplate.forStatus(status, partiallyFulfilled),
                )
            }
        }
    }

private fun notAnUpdateReason(op: String): String =
    if (op == "r") {
        "evento de snapshot não é mudança de status"
    } else {
        "PENDING é o estado inicial, não uma mudança de status"
    }

private fun nonNotifiableReason(afterStatus: String): String =
    if (afterStatus == "AWAITING_MASTER_DATA") {
        "a lacuna é de cadastro nosso, e o cliente não pode agir sobre ela"
    } else {
        "$afterStatus não é Notifiable Status"
    }

/**
 * Idempotência: transição já notificada não gera segunda mensagem.
 * A chave é a NotificationKey — (order_id, novo_status).
 */
fun isDuplicate(key: NotificationKey, seenKeys: Set<NotificationKey>): Boolean = key in seenKeys
