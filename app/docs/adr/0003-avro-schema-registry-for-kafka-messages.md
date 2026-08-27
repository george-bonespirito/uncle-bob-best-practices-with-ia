# Avro + Apicurio Registry para as mensagens Kafka (resultado das Processing Steps e CDC)

O projeto tem dois tópicos Kafka: resultado das Processing Steps (os 7 MS de dado → MS Order, ADR order/0003) e CDC do Order (Debezium → MS Notifications e consumidores externos, ADR order/0002). Nenhum dos dois tinha formato de serialização definido.

Decidimos Avro com Schema Registry para os dois — não só o tópico de resultado (mensagem de aplicação, escrita por nós), mas também o tópico CDC (onde o Debezium normalmente publicaria em JSON por default), configurando o connector do Debezium com `AvroConverter` apontando para o registry.

Registry escolhido: **Apicurio Registry** — mesma família de projeto do Debezium (Red Hat), integração mais documentada, Apache 2.0 sem ambiguidade de licença (Confluent Schema Registry tem Confluent Community License no componente — sem problema para uso self-hosted não-competitivo como este projeto, mas Apicurio evita a ressalva por completo). Self-hosted via container, consistente com o restante da infra do projeto.

Alternativa rejeitada: JSON simples sem registry — mais simples, sem infra nova, mas sem checagem de compatibilidade em tempo de publish; um producer podia divergir do formato esperado por um consumer e só estourar em runtime/teste. Foi a recomendação inicial, rejeitada deliberadamente porque testar Schema Registry/compatibilidade de schema em produção real é um objetivo de aprendizado válido para este projeto, mesmo não tendo sido listado nos objetivos originais (virtual threads/SKIP LOCKED/CDC/Kafka/Saga).

Trade-off aceito: mais um componente de infra (Apicurio Registry) e mais uma camada de configuração no connector do Debezium; compensado pelo ganho de aprendizado explícito e por unificar o mecanismo de serialização do projeto inteiro (um formato só, não dois coexistindo).

Status: accepted
