# Meta Cloud API direto para envio de WhatsApp, com template pré-aprovado

MS Notifications só envia mensagem (ADR notifications/0001) — sem webhook de entrada. Faltava decidir o provedor e o contrato de envio.

Decidimos **Meta Cloud API** (WhatsApp Cloud API oficial), chamada direta via `POST https://graph.facebook.com/v{version}/{phone_number_id}/messages`, sem intermediário (Twilio ou outro BSP). Motivo: é a API oficial, sem markup de terceiro, gratuita dentro de limites generosos — consistente com a preferência já demonstrada neste projeto por integrar direto com a coisa real em vez de uma camada de abstração comercial em cima dela (mesmo raciocínio de Avro nativo em vez de JSON simplificado, self-hosted em vez de gerenciado).

**Fato que restringe o design, não é decisão** — regra da própria Meta, vale pra qualquer provedor: mensagem iniciada pela empresa (fora de uma janela de 24h de atendimento) **exige template pré-aprovado**. Como toda notificação de mudança de status é iniciada pelo sistema (nunca resposta a mensagem do cliente), MS Notifications **sempre** envia via `type: "template"`, nunca texto livre.

**Template proposto:** um único template genérico cobrindo qualquer transição de `Order Status`, com duas variáveis — número do pedido e novo status — em vez de um template por status (menos aprovação pra manter, menos superfície). Texto exato e aprovação na Meta ficam pra quando a implementação começar; o contrato de variáveis (`{{1}}=source_order_number`, `{{2}}=novo status`) é o que importa agora.

**Credenciais** (access token da Meta App + `phone_number_id`) seguem o mesmo padrão já estabelecido (ADR raiz 0006): nunca versionadas, nem placeholder — Secret criado fora do Git, manifesto só referencia via `secretKeyRef`.

**Setup de desenvolvimento:** Meta libera número de teste + até 5 destinatários de teste sem exigir verificação de negócio — suficiente pra validar o fluxo ponta a ponta antes de qualquer aprovação formal de template/negócio.

Alternativa rejeitada: Twilio — sandbox mais rápido de configurar (número compartilhado, sem esperar aprovação de template pra testar), mas é wrapper comercial sobre a mesma API da Meta, com markup — rejeitado pela mesma razão que outras abstrações comerciais foram evitadas neste projeto.

Nota pra quando a linguagem de domínio de Notifications for resolvida (gray area #5, adiado): o consumer do tópico CDC nesse MS provavelmente merece o mesmo padrão de DLT do MS Order (ADR order/0007) pra envio de WhatsApp que falha — não desenhado agora, só sinalizado.

Status: accepted
