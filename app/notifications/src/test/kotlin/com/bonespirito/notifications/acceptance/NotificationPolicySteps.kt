package com.bonespirito.notifications.acceptance

import com.bonespirito.notifications.domain.policy.MessageTemplate
import com.bonespirito.notifications.domain.policy.NotificationDecision
import com.bonespirito.notifications.domain.policy.NotificationKey
import com.bonespirito.notifications.domain.policy.NotifiableStatus
import com.bonespirito.notifications.domain.policy.decideNotification
import com.bonespirito.notifications.domain.policy.isDuplicate
import com.bonespirito.notifications.domain.policy.renderMessage
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.ScenarioScope
import org.assertj.core.api.Assertions.assertThat

/**
 * Steps dos cenários cobertos pela frente de policies (architect-000055):
 * só decisão pura — sem Kafka, sem Meta, sem Customers, sem DLT.
 */
@ScenarioScope
class NotificationPolicySteps {

    private var orderId: String = ""
    private var beforeStatus: String = ""
    private var afterStatus: String = ""
    private var partiallyFulfilled: Boolean = false
    private var lastDecision: NotificationDecision? = null
    private var seenKeys: MutableSet<NotificationKey> = mutableSetOf()
    private var lastMessage: String? = null
    private var duplicate: Boolean = false

    @Given("um Order {string} com status {string}")
    fun orderComStatus(id: String, status: String) {
        orderId = id
        beforeStatus = status
    }

    @Given("um Order {string} em {string}")
    fun orderEmStatus(id: String, status: String) {
        orderId = id
        beforeStatus = status
    }

    @Given("um Order {string} com 3 itens e customer_snapshot preenchido")
    fun orderComItensESnapshot(id: String) {
        orderId = id
    }

    @Given("um Order {string} com 3 itens, um deles {string}")
    fun orderComItemNaoAtendido(id: String, itemStatus: String) {
        orderId = id
        assertThat(itemStatus).isEqualTo("NOT_FULFILLED")
    }

    @Given("um Order {string} com todos os itens {string}")
    fun orderComTodosItensAtendidos(id: String, itemStatus: String) {
        orderId = id
        assertThat(itemStatus).isEqualTo("FULFILLED")
    }

    @Given("um Order novo criado pelo import, com status {string}")
    fun orderNovo(status: String) {
        assertThat(status).isEqualTo("PENDING")
    }

    @Given("um Order {string} cuja transição para {string} já foi notificada")
    fun transicaoJaNotificada(id: String, status: String) {
        orderId = id
        afterStatus = status
        beforeStatus = "INVOICING"
        val decision = decideNotification(orderId, "u", beforeStatus, afterStatus, false)
        assertThat(decision).isInstanceOf(NotificationDecision.Notify::class.java)
        seenKeys.add((decision as NotificationDecision.Notify).key)
    }

    @Given("500 Orders existentes no banco, em diversos status")
    fun quinhentasOrdersExistentes() = Unit

    @And("o slot de replicação foi invalidado e o Debezium refez o snapshot")
    fun slotInvalidado() = Unit

    @And("a Invoice Generation gravou {string} na mesma transação da transição terminal")
    fun flagGravado(flag: String) {
        partiallyFulfilled = flag == "partially_fulfilled"
    }

    @And("{string} não está marcado")
    fun flagNaoMarcado(flag: String) {
        assertThat(flag).isEqualTo("partially_fulfilled")
        partiallyFulfilled = false
    }

    @When("um campo diferente de status é alterado no Order, sem mudança de status")
    fun campoDiferenteAlterado() {
        afterStatus = beforeStatus
        lastDecision = decideNotification(orderId, "u", beforeStatus, afterStatus, false)
    }

    @When("os 500 eventos de snapshot chegam no tópico CDC, com op {string}")
    fun eventosDeSnapshot(op: String) {
        assertThat(op).isEqualTo("r")
        repeat(500) {
            lastDecision = decideNotification(orderId, op, "", "PROCESSING", false)
            assertThat(lastDecision).isInstanceOf(NotificationDecision.Ignore::class.java)
        }
    }

    @When("o evento CDC de insert chega no tópico, com op {string}")
    fun eventoDeInsert(op: String) {
        assertThat(op).isEqualTo("c")
        lastDecision = decideNotification(orderId, op, "", "PENDING", false)
    }

    @When("o status muda para {string}")
    fun statusMudaPara(status: String) {
        afterStatus = status
        lastDecision = decideNotification(orderId, "u", beforeStatus, afterStatus, partiallyFulfilled)
    }

    @When("o status muda para {string} por cadastro faltando")
    fun statusMudaPorCadastroFaltando(status: String) {
        afterStatus = status
        lastDecision = decideNotification(orderId, "u", beforeStatus, afterStatus, false)
    }

    @When("o status do Order muda para {string}")
    fun statusDoOrderMuda(status: String) {
        afterStatus = status
        if (beforeStatus.isBlank()) {
            beforeStatus = "INVOICING"
        }
        lastDecision = decideNotification(orderId, "u", beforeStatus, afterStatus, partiallyFulfilled)
    }

    @When("o mesmo evento CDC chega novamente após reprocessamento do tópico")
    fun eventoReprocessado() {
        val decision = decideNotification(orderId, "u", beforeStatus, afterStatus, false)
        assertThat(decision).isInstanceOf(NotificationDecision.Notify::class.java)
        duplicate = isDuplicate((decision as NotificationDecision.Notify).key, seenKeys)
    }

    @When("a mensagem de mudança para {string} é montada")
    fun mensagemMontada(status: String) {
        val notifiable = NotifiableStatus.fromOrderStatus(status)
        assertThat(notifiable).isNotNull()
        lastMessage = renderMessage(MessageTemplate.forStatus(notifiable!!, false), orderId)
    }

    @Then("nenhuma mensagem WhatsApp é enviada")
    fun nenhumaMensagemEnviada() {
        assertThat(lastDecision).isInstanceOf(NotificationDecision.Ignore::class.java)
    }

    @Then("nenhuma mensagem adicional é enviada")
    fun nenhumaMensagemAdicional() {
        assertThat(duplicate).isTrue()
    }

    @And("a razão é que evento de snapshot não é mudança de status")
    fun razaoSnapshot() {
        assertThat((lastDecision as NotificationDecision.Ignore).reason)
            .isEqualTo("evento de snapshot não é mudança de status")
    }

    @And("a razão é que {string} é o estado inicial, não uma mudança de status")
    fun razaoEstadoInicial(status: String) {
        assertThat((lastDecision as NotificationDecision.Ignore).reason)
            .isEqualTo("$status é o estado inicial, não uma mudança de status")
    }

    @And("a razão é que {string} não é Notifiable Status")
    fun razaoStatusInterno(status: String) {
        assertThat((lastDecision as NotificationDecision.Ignore).reason)
            .isEqualTo("$status não é Notifiable Status")
    }

    @And("a razão é que a lacuna é de cadastro nosso, e o cliente não pode agir sobre ela")
    fun razaoCadastro() {
        assertThat((lastDecision as NotificationDecision.Ignore).reason)
            .isEqualTo("a lacuna é de cadastro nosso, e o cliente não pode agir sobre ela")
    }

    @And("a deduplicação usa a chave \\(order_id, novo_status\\)")
    fun dedupePorChave() {
        assertThat(seenKeys.single())
            .isEqualTo(NotificationKey(orderId, NotifiableStatus.COMPLETED))
    }

    @And("a razão é que o offset muda no reprocessamento e não serve como chave")
    fun razaoOffset() = Unit

    @Then("o template contém o número do pedido e o status")
    fun templateContemNumeroEStatus() {
        assertThat(lastMessage).contains(orderId).contains("PROCESSING")
    }

    @And("não contém nome, documento, endereço nem valores")
    fun templateSemDadoDeCliente() {
        listOf("nome", "documento", "endereço", "valor").forEach { dado ->
            assertThat(lastMessage).doesNotContainIgnoringCase(dado)
        }
    }

    @Then("MS Notifications envia a mensagem com o Partial Completion Template")
    fun enviaTemplateVariante() {
        assertThat(lastDecision).isEqualTo(
            NotificationDecision.Notify(
                key = NotificationKey(orderId, NotifiableStatus.COMPLETED),
                template = MessageTemplate.PARTIAL_COMPLETION,
            ),
        )
    }

    @And("a mensagem diz que houve item não atendido, sem dizer qual")
    fun mensagemDizItemNaoAtendido() {
        val decision = lastDecision as NotificationDecision.Notify
        assertThat(renderMessage(decision.template, orderId)).contains("item não atendido")
    }

    @And("apenas uma mensagem é enviada, porque o variante é escolha de template dentro da mesma transição")
    fun varianteSemSegundaMensagem() {
        val decision = lastDecision as NotificationDecision.Notify
        assertThat(decision.key).isEqualTo(NotificationKey(orderId, NotifiableStatus.COMPLETED))
    }

    @Then("o template usado é o de conclusão, não o variante")
    fun templateDeConclusao() {
        assertThat(lastDecision).isEqualTo(
            NotificationDecision.Notify(
                key = NotificationKey(orderId, NotifiableStatus.COMPLETED),
                template = MessageTemplate.COMPLETED,
            ),
        )
    }
}
