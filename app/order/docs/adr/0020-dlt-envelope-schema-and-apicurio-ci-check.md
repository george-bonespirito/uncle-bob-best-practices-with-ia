# Envelope Avro único pros 7 tópicos DLT, com compatibility check via Apicurio no Woodpecker

ADR raiz 0005 criou um tópico `.dlt` por tópico de resultado, mas nunca definiu o schema dessas mensagens. ADR raiz 0003 decidiu Avro + Apicurio Registry pros tópicos de resultado e CDC, sem cobrir DLT. ADR order/0008 fechou o pipeline Woodpecker (`gradlew check` → build → push → bump do manifesto → ArgoCD), sem step de schema. Ficava em aberto a gray area #12 da PRD: "mecanismo de contract test do schema Avro em CI (compatibility check antes de publish) — não desenhado".

## Escopo: só os 7 tópicos DLT

O CI de schema cobre exclusivamente os 7 tópicos `.dlt` — não o CDC nem os 7 tópicos de resultado.

- **CDC fica fora**: o conector Debezium (`AvroConverter`, ADR raiz 0003) infere o schema a partir da tabela Postgres e registra sozinho no Apicurio em runtime, na primeira mensagem. Não existe `.avsc` autoral pra versionar, e o conector roda no cluster de infra — não há o que um CI deste repo faça aqui.
- **Tópicos de resultado ficam fora**: MS Order só consome essas 7 mensagens, via `GenericRecord` resolvido em runtime contra o Apicurio — sem gerar `SpecificRecord`/classes Kotlin tipadas, então sem `.avsc` local a manter. As 7 MS de dado que produzem essas mensagens ainda são gray area (não implementadas); não há schema delas pra sincronizar nesta ponta.

**Alternativa rejeitada**: gerar `SpecificRecord` a partir de `.avsc` pros tópicos de resultado, pra ganhar tipagem em compile-time. Rejeitada porque exigiria manter localmente uma cópia do schema autorado por serviços que ainda não existem — fonte de drift garantida, sem os produtores reais pra validar contra.

## Um envelope genérico único, não um schema por tópico

Os 7 tópicos `.dlt` compartilham um único schema Avro — `orderId`, `resultId`, `topicOriginal`, `payloadOriginal` (bytes, opaco), `failureReason`, `failedAt`, `attempt` — em vez de um schema específico por tópico espelhando o payload original.

Motivo: o propósito do DLT (ADR raiz 0005) é guardar a mensagem que falhou mais metadados de erro pra resolução manual — não o shape tipado do payload de origem. Um schema por tópico seria quase-duplicado 7 vezes, sem ganho real: a resolução manual já precisa olhar `payloadOriginal` bruto de qualquer forma quando o problema é justamente uma mensagem malformada.

Arquivo: `app/order/src/main/resources/avro/dlt-envelope.avsc`.

## Naming no Apicurio: `RecordNameStrategy`, não `TopicNameStrategy`

O envelope é registrado como uma única subject — `com.bonespirito.order.dlt.DltEnvelope` — reutilizada pelos 7 tópicos `.dlt`.

**Alternativa rejeitada**: `TopicNameStrategy` (convenção default), que criaria 7 subjects distintas com conteúdo idêntico. Rejeitada porque um envelope genuinamente compartilhado não deveria ter 7 cópias registradas — risco real de alguém atualizar uma subject e esquecer as outras 6, deixando o Apicurio afirmar 7 "versões atuais" divergentes de um schema que é, por design, o mesmo.

## Modo de compatibilidade: `BACKWARD`

Consistente com a resolução manual do DLT (ADR raiz 0005): ferramentas de inspeção antigas continuam lendo mensagens novas do envelope. Não é `FULL` nem `FORWARD` porque não há necessidade de travar a evolução do envelope nos dois sentidos — o consumo de DLT é sempre humano, ad-hoc, nunca um consumer automatizado que precise ler versões futuras com código antigo.

## Mecanismo de CI: script direto no Woodpecker, sem Gradle

Sem plugin Gradle oficial do Apicurio (achado do research desta sessão), o step roda como script direto (chamada REST) no Woodpecker deste repo — ao lado do step `gradlew check` já decidido em order/0008 — em vez de virar uma task Gradle customizada.

O script é parametrizado pela variável nativa `CI_PIPELINE_EVENT`:

- **PR**: chama o endpoint de *test* da Apicurio REST API v3 — verifica compatibilidade sem persistir.
- **push em `main`**: chama o endpoint de criar nova versão — publish real, só depois de já ter passado pelo mesmo check como dry-run na PR que originou o merge.

Em caso de incompatibilidade, o corpo de erro do Apicurio (regra de compatibilidade violada) é propagado pro stdout do step, que sai com código não-zero — falha o step do Woodpecker, sem bot de comentário em PR. Não há gate mais sofisticado porque o projeto é solo e de aprendizado; o log do step já é a superfície de erro.

**Alternativa rejeitada**: task Gradle customizada chamando a API via HTTP client na JVM. Rejeitada por adicionar tooling Gradle pra um REST call simples, quando um script standalone no `.woodpecker.yml` resolve com menos código e sem acoplar o schema check ao build da aplicação.

## Consequências

Fecha a gray area #12 da PRD. Deixa em aberto (fora desta ADR) a sintaxe exata do novo step no `.woodpecker.yml` deste repo — implementação, não desenho.

Status: accepted
