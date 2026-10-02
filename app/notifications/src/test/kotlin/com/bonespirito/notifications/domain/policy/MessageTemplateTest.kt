package com.bonespirito.notifications.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageTemplateTest {

    @Test
    fun `completado parcial usa variante`() {
        assertThat(MessageTemplate.forStatus(NotifiableStatus.COMPLETED, true))
            .isEqualTo(MessageTemplate.PARTIAL_COMPLETION)
    }

    @Test
    fun `completado sem parcial usa template normal`() {
        assertThat(MessageTemplate.forStatus(NotifiableStatus.COMPLETED, false))
            .isEqualTo(MessageTemplate.COMPLETED)
    }

    @Test
    fun `processing e cancelled tem template proprio`() {
        assertThat(MessageTemplate.forStatus(NotifiableStatus.PROCESSING, false))
            .isEqualTo(MessageTemplate.PROCESSING)
        assertThat(MessageTemplate.forStatus(NotifiableStatus.CANCELLED, false))
            .isEqualTo(MessageTemplate.CANCELLED)
    }

    @Test
    fun `mensagem carrega numero do pedido e status, nada mais`() {
        val message = renderMessage(MessageTemplate.PROCESSING, "#123")

        assertThat(message).isEqualTo("Pedido #123: status PROCESSING")
        assertThat(message).doesNotContain("nome", "documento", "endereço", "valor")
    }

    @Test
    fun `variante de conclusao parcial diz item nao atendido sem dizer qual`() {
        val message = renderMessage(MessageTemplate.PARTIAL_COMPLETION, "#123")

        assertThat(message).contains("#123", "COMPLETED", "item não atendido")
    }
}
