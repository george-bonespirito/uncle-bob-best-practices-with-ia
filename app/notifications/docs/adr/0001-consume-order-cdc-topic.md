# MS Notifications consome o tópico CDC do Order em vez de um evento de domínio próprio

MS Notifications precisa avisar o cliente via WhatsApp a cada mudança de status do Order. MS Order já publica todo change da tabela `orders` via Debezium/CDC no Kafka (ADR 0002 do contexto Order).

Decidimos que MS Notifications consome esse mesmo tópico CDC, filtrando eventos onde `before.status != after.status`, em vez de MS Order publicar um evento de domínio explícito (`OrderStatusChanged`) via outbox.

Alternativa considerada: evento de domínio explícito, desacoplado do schema da tabela `orders` — contrato mais estável, mas reintroduziria o outbox pattern que ADR 0002 decidiu não usar, com dois mecanismos de publicação coexistindo no mesmo contexto (Debezium pro CDC geral + outbox só pra esse evento).

Trade-off aceito conscientemente: MS Notifications fica acoplado ao schema da tabela `orders` — renomear ou remover a coluna `status` quebra o consumer sem aviso em tempo de compilação. Aceitável no escopo de aprendizado deste projeto; numa API pública real de verdade isso pesaria mais a favor do evento de domínio explícito.

Status: accepted
