# Debezium (CDC via WAL) para publicar mudanças do Order no Kafka

Depois da importação em massa da planilha (.xlsx/.csv) e do processamento pelas Processing Steps, mudanças no Order precisam ficar disponíveis pra consumidores externos via Kafka.

Não existe publicação nativa Postgres→Kafka — as alternativas reais eram Debezium (lê o WAL via slot de replicação lógica do Postgres, captura toda mudança automaticamente, sem código na aplicação) ou Outbox pattern (tabela `order_event` populada na mesma transação + publisher explícito via `spring-kafka`).

Decidimos Debezium. Motivo: é a ferramenta que motivou a pergunta original do projeto (aprender CDC de verdade é um objetivo explícito), e captura mudanças automaticamente sem exigir que toda escrita no Order passe por um caminho de código que lembra de popular um outbox. Trade-off aceito conscientemente: mais infraestrutura (Kafka Connect + container Debezium + registro de connector via API REST), maior superfície operacional do que um outbox simples.

Status: accepted

**Revisão (2026-09-03) — o slot de replicação não é eterno neste ambiente.** O cluster k3s define `max_slot_wal_keep_size = 2GB` (commit `25fd457` do repo de infra), teto necessário porque o PVC é `local-path` num disco compartilhado de 98G e o cluster é hibernado de propósito — slot parado acumulando WAL sem teto encheria o disco. A consequência para esta decisão: **o slot pode ser invalidado, e o conector precisa tolerar isso e ressincronizar.** Não é cenário raro aqui; acontece sempre que a hibernação durar o suficiente. O outbox pattern rejeitado acima não teria esse modo de falha — é um custo do Debezium que não estava contabilizado quando esta ADR foi escrita, e que ainda assim não inverte a decisão (aprender CDC de verdade continua sendo o objetivo, e ressincronizar é parte de operar CDC de verdade). O efeito colateral perigoso do re-snapshot sobre o MS Notifications está tratado em [ADR notifications/0001](../../../notifications/docs/adr/0001-consume-order-cdc-topic.md).
