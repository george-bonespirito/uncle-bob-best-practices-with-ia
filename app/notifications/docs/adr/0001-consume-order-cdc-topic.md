# MS Notifications consome o tópico CDC do Order em vez de um evento de domínio próprio

MS Notifications precisa avisar o cliente via WhatsApp a cada mudança de status do Order. MS Order já publica todo change da tabela `orders` via Debezium/CDC no Kafka (ADR 0002 do contexto Order).

Decidimos que MS Notifications consome esse mesmo tópico CDC, filtrando eventos onde `before.status != after.status`, em vez de MS Order publicar um evento de domínio explícito (`OrderStatusChanged`) via outbox.

Alternativa considerada: evento de domínio explícito, desacoplado do schema da tabela `orders` — contrato mais estável, mas reintroduziria o outbox pattern que ADR 0002 decidiu não usar, com dois mecanismos de publicação coexistindo no mesmo contexto (Debezium pro CDC geral + outbox só pra esse evento).

Trade-off aceito conscientemente: MS Notifications fica acoplado ao schema da tabela `orders` — renomear ou remover a coluna `status` quebra o consumer sem aviso em tempo de compilação. Aceitável no escopo de aprendizado deste projeto; numa API pública real de verdade isso pesaria mais a favor do evento de domínio explícito.

Status: accepted

**Revisão (2026-09-03) — o filtro precisa ignorar evento de snapshot, não só comparar status.** O filtro `before.status != after.status`, como escrito acima, é insuficiente e teria consequência grave. Quando o slot de replicação é invalidado — evento **esperado** neste cluster, ver revisão da [ADR order/0002](../../../order/docs/adr/0002-debezium-for-order-cdc.md) — o Debezium refaz o snapshot e reemite **toda linha da tabela `orders`** com `op: "r"`. Esses eventos não carregam estado anterior: não houve mudança, então `before` não representa "como estava antes". O filtro passaria em todos eles, e o MS Notifications enviaria uma mensagem de WhatsApp real, via Meta Cloud API, para cada cliente com pedido no banco — inclusive pedidos antigos que não mudaram de status.

O filtro correto tem duas condições: **`op` deve ser `u` (update)** — descartando `r` (snapshot) e `c` (insert), que também tem `before` nulo — **e** `before.status != after.status`. Um Order recém-criado não gera notificação, o que é o comportamento certo: `PENDING` é o estado inicial, não uma mudança de status.

Esta é a lacuna mais perigosa encontrada até agora no design, e existia desde que esta ADR foi escrita — só apareceu quando a sessão de infra tornou a invalidação de slot um evento rotineiro.
