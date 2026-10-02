package com.bonespirito.notifications.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationDecisionTest {

    @Test
    fun `transicao para PROCESSING notifica`() {
        val decision = decideNotification(
            orderId = "#123",
            op = "u",
            beforeStatus = "PENDING",
            afterStatus = "PROCESSING",
            partiallyFulfilled = false,
        )

        assertThat(decision).isEqualTo(
            NotificationDecision.Notify(
                key = NotificationKey("#123", NotifiableStatus.PROCESSING),
                template = MessageTemplate.PROCESSING,
            ),
        )
    }

    @Test
    fun `transicao para CANCELLED notifica`() {
        val decision = decideNotification("#123", "u", "PROCESSING", "CANCELLED", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Notify(
                key = NotificationKey("#123", NotifiableStatus.CANCELLED),
                template = MessageTemplate.CANCELLED,
            ),
        )
    }

    @Test
    fun `transicao para COMPLETED sem parcial notifica com template normal`() {
        val decision = decideNotification("#123", "u", "INVOICING", "COMPLETED", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Notify(
                key = NotificationKey("#123", NotifiableStatus.COMPLETED),
                template = MessageTemplate.COMPLETED,
            ),
        )
    }

    @Test
    fun `transicao para COMPLETED com parcial usa template variante`() {
        val decision = decideNotification("#123", "u", "INVOICING", "COMPLETED", true)

        assertThat(decision).isEqualTo(
            NotificationDecision.Notify(
                key = NotificationKey("#123", NotifiableStatus.COMPLETED),
                template = MessageTemplate.PARTIAL_COMPLETION,
            ),
        )
    }

    @Test
    fun `mudanca em outro campo nao notifica`() {
        val decision = decideNotification("#123", "u", "PROCESSING", "PROCESSING", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Ignore("mudança em outro campo não é mudança de status"),
        )
    }

    @Test
    fun `evento de snapshot nao notifica`() {
        val decision = decideNotification("#123", "r", "", "PROCESSING", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Ignore("evento de snapshot não é mudança de status"),
        )
    }

    @Test
    fun `insert nao notifica`() {
        val decision = decideNotification("#123", "c", "", "PENDING", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Ignore("PENDING é o estado inicial, não uma mudança de status"),
        )
    }

    @Test
    fun `delete nao notifica`() {
        val decision = decideNotification("#123", "d", "COMPLETED", "", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Ignore("PENDING é o estado inicial, não uma mudança de status"),
        )
    }

    @Test
    fun `status interno nao notifica`() {
        listOf("INVOICING", "INVOICE_FAILED", "COMPENSATING", "COMPENSATION_FAILED").forEach { status ->
            val decision = decideNotification("#123", "u", "PROCESSING", status, false)

            assertThat(decision)
                .isEqualTo(NotificationDecision.Ignore("$status não é Notifiable Status"))
        }
    }

    @Test
    fun `awaiting master data nao notifica com razao propria`() {
        val decision = decideNotification("#123", "u", "PROCESSING", "AWAITING_MASTER_DATA", false)

        assertThat(decision).isEqualTo(
            NotificationDecision.Ignore(
                "a lacuna é de cadastro nosso, e o cliente não pode agir sobre ela",
            ),
        )
    }

    @Test
    fun `pendente como destino nao notifica`() {
        val decision = decideNotification("#123", "u", "PROCESSING", "PENDING", false)

        assertThat(decision)
            .isEqualTo(NotificationDecision.Ignore("PENDING não é Notifiable Status"))
    }

    @Test
    fun `transicao ja vista e duplicata`() {
        val key = NotificationKey("#123", NotifiableStatus.COMPLETED)

        assertThat(isDuplicate(key, setOf(key))).isTrue()
    }

    @Test
    fun `transicao nova nao e duplicata`() {
        val key = NotificationKey("#123", NotifiableStatus.PROCESSING)

        assertThat(isDuplicate(key, setOf(NotificationKey("#123", NotifiableStatus.COMPLETED)))).isFalse()
    }
}
