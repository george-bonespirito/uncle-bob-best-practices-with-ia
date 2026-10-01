package com.bonespirito.notifications.domain.policy

/**
 * Os status do Order que geram Notification (ADR notifications/0004):
 * PROCESSING, CANCELLED e COMPLETED. Os demais não notificam — PENDING é
 * estado inicial e não é transição; INVOICING, INVOICE_FAILED, COMPENSATING
 * e COMPENSATION_FAILED são internos; AWAITING_MASTER_DATA é lacuna de
 * cadastro nossa, sobre a qual o cliente não pode agir.
 */
enum class NotifiableStatus(val orderStatus: String) {
    PROCESSING("PROCESSING"),
    CANCELLED("CANCELLED"),
    COMPLETED("COMPLETED"),
    ;

    companion object {
        fun fromOrderStatus(status: String): NotifiableStatus? =
            entries.firstOrNull { it.orderStatus == status }
    }
}
