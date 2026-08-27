# Debezium (CDC via WAL) para publicar mudanças do Order no Kafka

Depois da importação em massa da planilha (.xlsx/.csv) e do processamento pelas Processing Steps, mudanças no Order precisam ficar disponíveis pra consumidores externos via Kafka.

Não existe publicação nativa Postgres→Kafka — as alternativas reais eram Debezium (lê o WAL via slot de replicação lógica do Postgres, captura toda mudança automaticamente, sem código na aplicação) ou Outbox pattern (tabela `order_event` populada na mesma transação + publisher explícito via `spring-kafka`).

Decidimos Debezium. Motivo: é a ferramenta que motivou a pergunta original do projeto (aprender CDC de verdade é um objetivo explícito), e captura mudanças automaticamente sem exigir que toda escrita no Order passe por um caminho de código que lembra de popular um outbox. Trade-off aceito conscientemente: mais infraestrutura (Kafka Connect + container Debezium + registro de connector via API REST), maior superfície operacional do que um outbox simples.

Status: accepted
