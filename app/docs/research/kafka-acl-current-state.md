# Kafka atual (Bitnami KRaft): autorizador/ACL habilitado?

Pesquisa referente ao ticket Codeberg #3, parte do mapa wayfinder #1 ("Kafka/Avro registry as code").

**Data da pesquisa:** 2026-08-29
**Fonte primária dos values:** repo privado `bonespirito-cluster-k3s-infra` (Codeberg), arquivo `apps/kafka-app.yaml`, lido via clone read-only descartável.

## TL;DR

- **Autenticação (SASL): habilitada.** Não por configuração explícita no `kafka-app.yaml`, mas porque o **default do chart Bitnami `kafka` 32.4.3** já é `listeners.client.protocol: SASL_PLAINTEXT`, e o cluster aponta `sasl.existingSecret` para um Secret real com credenciais.
- **Autorização/ACL: NÃO habilitada.** Nenhum valor do chart, em nenhuma camada (values do `kafka-app.yaml`, defaults do chart, templates do chart), define `authorizer.class.name`. A ausência dessa propriedade não é "modo restritivo com allow-list vazia" — é **ausência total de enforcement de autorização**: qualquer principal autenticado via SASL tem acesso irrestrito a todos os tópicos e operações.
- **Modelo "1 usuário Kafka por microsserviço" (9 MS): parcialmente nativo.** O chart suporta múltiplos usuários SASL via values (`sasl.client.users`/`sasl.client.passwords`), mas **não tem nenhum mecanismo de values para ACL por usuário**. O job de provisionamento do chart (`templates/provisioning/job.yaml`) só cria tópicos (`kafka-topics.sh`) — não roda `kafka-acls.sh`. Restringir cada MS ao(s) tópico(s) que ele deveria tocar exigiria um passo manual/imperativo pós-deploy.

## 1. O que está configurado hoje

Trecho relevante de `apps/kafka-app.yaml` no repo de infra (chart `oci://registry-1.docker.io/bitnamicharts/kafka`, `targetRevision: 32.4.3`, modo KRaft):

```yaml
        existingKraftSecret: kafka-kraft
        sasl:
          existingSecret: kafka-user-passwords
        ...
        kraft:
          enabled: true
        zookeeper:
          enabled: false
```

Não há, em nenhum lugar do arquivo:
- bloco `listeners:` (protocolo de cada listener não é sobrescrito)
- bloco `auth:`
- qualquer chave contendo `authorizer`
- `config:`, `overrideConfiguration:`, `secretConfig:` ou `existingSecretConfig:` (os mecanismos do chart para injetar `server.properties` arbitrário) — nem no nível raiz nem em `broker:`/`controller:`

Ou seja: tudo que rege autenticação/autorização vem do **default do chart**, exceto o ponteiro para o Secret de senhas.

## 2. Defaults do chart Bitnami `kafka` (cruzado com `values.yaml` da branch `main` de `bitnami/charts`)

Fonte: `https://raw.githubusercontent.com/bitnami/charts/main/bitnami/kafka/values.yaml` (branch `main`, chart em desenvolvimento ativo — usado aqui como referência da estrutura de values da major line 32.x; não há garantia de ser byte-a-byte idêntico ao values.yaml congelado da release 32.4.3, mas a estrutura de parâmetros relevantes — `sasl.*`, `listeners.*`, ausência de `authorizer*` — é estável nessa linha do chart).

```yaml
## @section Kafka SASL parameters
sasl:
  enabledMechanisms: PLAIN,SCRAM-SHA-256,SCRAM-SHA-512
  interBrokerMechanism: PLAIN
  controllerMechanism: PLAIN
```
> "NOTE: At the moment, Kafka Raft mode does not support SCRAM, that is why only PLAIN is configured." (comentário no próprio values.yaml, para inter-broker/controller)

```yaml
listeners:
  client:
    containerPort: 9092
    protocol: SASL_PLAINTEXT
    name: CLIENT
    sslClientAuth: ""
```

`sasl.client.users` — default `- user1` — "Comma-separated list of usernames for client communications when SASL is enabled".

**Busca por `authorizerClassName`, `allowEveryoneIfNoAclFound`, `superUsers` no values.yaml inteiro: nenhuma ocorrência.** Confirmado por duas buscas independentes no arquivo completo.

Confirmado também que **não há** essas chaves nos templates do chart (`templates/broker/`, `templates/controller-eligible/`, `templates/provisioning/job.yaml`) — busca de código no repo `bitnami/charts` restrita a `path:bitnami/kafka` por `authorizer` não retornou nenhum arquivo.

**Mecanismo de override manual existe e não foi usado:** o chart expõe `config`/`overrideConfiguration`/`secretConfig`/`existingSecretConfig` (raiz e por `broker:`/`controller:`) exatamente para injetar propriedades arbitrárias de `server.properties` — seria o caminho para adicionar `authorizer.class.name` sem esperar um values dedicado. `kafka-app.yaml` não usa nenhum desses campos hoje.

## 3. Comportamento do Kafka sem `authorizer.class.name` (fonte: Apache Kafka, código-fonte + docs oficiais)

Definição do default, direto do código-fonte do broker (branch `trunk`, `apache/kafka`):

```java
// server-common/src/main/java/org/apache/kafka/server/config/ServerConfigs.java, linhas 123-126
public static final String AUTHORIZER_CLASS_NAME_CONFIG = "authorizer.class.name";
public static final String AUTHORIZER_CLASS_NAME_DEFAULT = "";
```

Default é **string vazia** — nenhum authorizer plugin carregado. Documentação oficial (`docs/security/authorization-and-acls.md`, `apache/kafka` trunk):

> "Kafka ships with a pluggable authorization framework, which is configured with the `authorizer.class.name` property in the server configuration. [...] For KRaft clusters, use the following configuration on all nodes [...]: `authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer`"

> "*Behavior Without ACLs:* If a resource (R) does not have any ACLs defined, meaning that no ACL matches the resource, Kafka will restrict access to that resource. In this situation, only super users are allowed to access it."

**Atenção à leitura correta desse trecho:** essa frase descreve o comportamento **depois que um authorizer já está configurado** (ex.: `StandardAuthorizer` ativo, mas o tópico X ainda não tem ACL — nesse caso só super users acessam X). **Não é o estado do cluster atual.** Com `authorizer.class.name` vazio (nosso caso), o framework de autorização inteiro está desligado — não há checagem alguma, e todo principal autenticado via SASL tem acesso irrestrito a tudo. A distinção entre "authorizer ligado sem ACL pra esse recurso" (restringe) e "authorizer nunca configurado" (não restringe nada) não está explicitada lado a lado no texto da doc — é inferida cruzando a doc com o default `""` do código-fonte acima, que é a fonte mais direta possível (a própria definição do parâmetro).

Fontes:
- `https://github.com/apache/kafka/blob/trunk/server-common/src/main/java/org/apache/kafka/server/config/ServerConfigs.java` (linhas 123-126)
- `https://github.com/apache/kafka/blob/trunk/docs/security/authorization-and-acls.md`

## 4. O que falta pra habilitar SASL/SCRAM + ACL por usuário

Via `values` do chart Bitnami (sem sair do modelo declarativo do ArgoCD):

1. **Authorizer** — usar `overrideConfiguration` (ou `config`) em `kafka-app.yaml` pra injetar:
   ```yaml
   overrideConfiguration:
     authorizer.class.name: org.apache.kafka.metadata.authorizer.StandardAuthorizer
     super.users: "User:kafka-admin"   # principal(is) que não passam por ACL
     allow.everyone.if.no.acl.found: "false"   # manter default explícito — negar por ausência de ACL
   ```
   Isso liga o enforcement mas **sem nenhuma ACL cadastrada, todo tópico fica inacessível a todo mundo exceto super.users** até as ACLs serem criadas — mudança precisa ser sequenciada com o passo 2, não pode ir sozinha pra produção.

2. **Mecanismo SASL** — trocar `PLAIN` por `SCRAM-SHA-256`/`SCRAM-SHA-512` se o objetivo é não ter senha em texto plano no JAAS: `sasl.enabledMechanisms` já inclui os dois SCRAM por default; falta apontar os listeners pra um deles especificamente e cadastrar os usuários como SCRAM credentials (mecanismo do próprio Kafka, não do chart — chart só empurra usuário/senha, o broker decide o mechanism aceito pelo listener).

3. **ACL por usuário — não existe via values.** O provisioning job do chart (`templates/provisioning/job.yaml`) só roda `kafka-topics.sh` para criar tópicos; não há chamada a `kafka-acls.sh` em nenhum lugar do chart. Para o modelo "1 usuário Kafka por MS", com 9 microsserviços (ver `app/CONTEXT-MAP.md`), a única forma hoje é:
   - **imperativa/manual**: `kafka-acls.sh --add --allow-principal User:<ms> --operation Write --topic <topico>` por MS, rodado contra o cluster depois do deploy (via `kubectl exec` num pod do broker, ou de fora com `--bootstrap-server`); ou
   - **customizada**: escrever um Job/Hook próprio (fora do chart, ex. um `postSync` hook do ArgoCD ou um Job Kubernetes adicional no repo de infra) que roda `kafka-acls.sh` de forma declarativa a cada sync — não é suportado nativamente pelo chart, seria construção adicional no repo `bonespirito-cluster-k3s-infra`.

   Não há terceira opção nos values do chart 32.4.3 — confirmado pela ausência total de `acl` no schema de values e nos templates.

## 5. Referências consultadas

| Fonte | URL | Uso |
|---|---|---|
| `kafka-app.yaml` (repo privado, clone read-only) | `git@codeberg.org:torgge/bonespirito-cluster-k3s-infra.git` | Estado real configurado |
| Bitnami kafka chart `values.yaml` | `https://raw.githubusercontent.com/bitnami/charts/main/bitnami/kafka/values.yaml` | Defaults de `sasl.*`, `listeners.*`, ausência de `authorizer*` |
| Bitnami kafka chart `README.md` | `https://raw.githubusercontent.com/bitnami/charts/main/bitnami/kafka/README.md` | Confirmação textual de como SASL é ligado |
| Bitnami kafka chart templates | `https://github.com/bitnami/charts/tree/main/bitnami/kafka/templates` (`provisioning/job.yaml` e busca de código por `authorizer`) | Confirma provisioning só cria tópicos, sem ACL |
| Apache Kafka — Authorization and ACLs | `https://github.com/apache/kafka/blob/trunk/docs/security/authorization-and-acls.md` | Semântica de `authorizer.class.name`, `allow.everyone.if.no.acl.found`, `super.users` |
| Apache Kafka — `ServerConfigs.java` | `https://github.com/apache/kafka/blob/trunk/server-common/src/main/java/org/apache/kafka/server/config/ServerConfigs.java` (linhas 123-126) | Default real de `authorizer.class.name` = `""` |

## Incertezas marcadas

- O `values.yaml` da branch `main` do repo `bitnami/charts` é a versão em desenvolvimento contínuo, não um tarball congelado da release exata `32.4.3`. A estrutura dos parâmetros citados (`sasl.*`, `listeners.client.protocol`, ausência de qualquer `authorizer*`) é estável nessa linha major do chart, mas não foi cruzada byte-a-byte contra o tarball publicado da 32.4.3 especificamente — se o comportamento precisar ser validado com certeza absoluta, o próximo passo seria baixar o chart pinado (`helm pull oci://registry-1.docker.io/bitnamicharts/kafka --version 32.4.3`) e inspecionar o `values.yaml` extraído.
- Não foi possível confirmar via cluster ao vivo (`kubectl exec` + `kafka-configs.sh --describe`) que o broker em execução de fato não tem `authorizer.class.name` setado por algum outro caminho (ex. ConfigMap editado manualmente fora do Git) — a conclusão é inteiramente baseada no que está declarado no Git (`kafka-app.yaml`) mais os defaults do chart, consistente com a premissa do ADR-001 da infra de que "o Git é a fonte da verdade do cluster".
