package com.bonespirito.notifications.domain.policy

/**
 * Chave de idempotência da Notification: (order_id, novo_status).
 * O CDC é at-least-once; a chave garante uma mensagem por transição.
 * Dedupe por offset não serve — o offset muda quando o tópico é reprocessado.
 */
data class NotificationKey(
    val orderId: String,
    val newStatus: NotifiableStatus,
)
