package com.bonespirito.notifications.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotifiableStatusTest {

    @Test
    fun `reconhece os tres status notificaveis`() {
        assertThat(NotifiableStatus.fromOrderStatus("PROCESSING")).isEqualTo(NotifiableStatus.PROCESSING)
        assertThat(NotifiableStatus.fromOrderStatus("CANCELLED")).isEqualTo(NotifiableStatus.CANCELLED)
        assertThat(NotifiableStatus.fromOrderStatus("COMPLETED")).isEqualTo(NotifiableStatus.COMPLETED)
    }

    @Test
    fun `status nao notificavel retorna nulo`() {
        listOf("PENDING", "AWAITING_MASTER_DATA", "INVOICING", "INVOICE_FAILED", "COMPENSATING", "COMPENSATION_FAILED")
            .forEach { status ->
                assertThat(NotifiableStatus.fromOrderStatus(status)).isNull()
            }
    }
}
