# Apicurio Registry: o que oferece pra CI/CD?

Pesquisa contra fontes primárias (documentação oficial Apicurio Registry, Maven Central, código-fonte/spec OpenAPI do repositório `Apicurio/apicurio-registry`) para o ticket Codeberg #2, parte do mapa wayfinder issue #1 (Kafka/Avro registry as code).

Versão da documentação usada como referência: **3.3.x** (`https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/index.html` — a raiz `/registry/docs/` redireciona para essa versão como a mais recente no momento da pesquisa).

Contexto do projeto: ADR `0003` decide Avro + Apicurio Registry para os tópicos Kafka; ADR `0004` decide 1 tópico por task do Conductor (7 tópicos, cada um com schema Avro próprio, sem union type — trade-off explícito contra usar `TopicRecordNameStrategy` num tópico único); ADR `0007` já define Woodpecker CI (self-hosted, integrado ao Codeberg) como mecanismo de CI/CD.

---

## 1. Maven plugin oficial para registrar/validar schema Avro no build

Existe, first-party, publicado no Maven Central.

- **GroupId/ArtifactId:** `io.apicurio:apicurio-registry-maven-plugin`
- **Versão confirmada no Maven Central:** `3.3.2` (consistente com a doc 3.3.x)
  Fonte: `https://central.sonatype.com/artifact/io.apicurio/apicurio-registry-maven-plugin`
- **Doc oficial:** `https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-managing-registry-artifacts-maven.html`

### Goals confirmados na doc
| Goal | Descrição (fonte) |
|---|---|
| `register` | Upload do artifact de schema/API pro Apicurio Registry |
| `download` | Baixa artifacts do Apicurio Registry pra arquivos locais |
| `test-update` | Testa o artifact contra as regras configuradas (ex.: compatibilidade) **sem gravar** no registry — é o goal relevante pra um gate de CI que valida compatibilidade antes de mergear/publicar |

Suporta os formatos Avro, GraphQL, AsyncAPI, OpenAPI, JSON Schema e Protobuf.

### Exemplo de configuração (adaptado da doc oficial, mesma fonte acima)
```xml
<plugin>
  <groupId>io.apicurio</groupId>
  <artifactId>apicurio-registry-maven-plugin</artifactId>
  <version>${apicurio.version}</version>
  <executions>
    <execution>
      <phase>generate-sources</phase>
      <goals>
        <goal>register</goal>
      </goals>
      <configuration>
        <registryUrl>MY-REGISTRY-URL/apis/registry/v3</registryUrl>
        <authServerUrl>MY-AUTH-SERVER</authServerUrl>
        <clientId>MY-CLIENT-ID</clientId>
        <clientSecret>MY-CLIENT-SECRET</clientSecret>
        <clientScope>MY-CLIENT-SCOPE</clientScope>
        <artifacts>
          <artifact>
            <groupId>TestGroup</groupId>
            <artifactId>FullNameRecord</artifactId>
            <file>${project.basedir}/src/main/resources/schemas/record.avsc</file>
            <ifExists>FAIL</ifExists>
          </artifact>
        </artifacts>
      </configuration>
    </execution>
  </executions>
</plugin>
```
`ifExists` controla o comportamento quando já existe uma versão do artifact (`FAIL`, e outras opções de update/return existentes — não detalhadas na íntegra na página consultada).

**Relevância pro projeto:** goal `test-update` no `check`/build do Gradle-equivalente (aqui o stack é Gradle, não Maven — plugin é Maven-only; ver nota de gap abaixo) validaria compatibilidade Avro antes de aplicar o schema em produção, encaixando no "Quality Gate" do CLAUDE.md.

**Gap identificado (não é falha de pesquisa, é característica do produto):** plugin é Maven-only. Não há Gradle plugin oficial equivalente listado nesta doc — não pesquisado a fundo pois fora do escopo das 5 perguntas, mas vale nota pro ADR/spec de CI/CD do projeto, já que o `CLAUDE.md` global do usuário determina Gradle Kotlin DSL como build canônico. Alternativa nesse caso: chamar a REST API diretamente (curl/script) ou o CLI (`apicurio-registry-cli`, mencionado no README do repo mas fora do escopo desta pesquisa).

---

## 2. REST API — endpoints relevantes

API v3 (`/apis/registry/v3`), confirmada via spec OpenAPI oficial do repositório:
`https://raw.githubusercontent.com/Apicurio/apicurio-registry/main/common/src/main/resources/META-INF/openapi.json`
(complementado pela doc: `https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-managing-registry-artifacts-api.html`)

| Ação | Método + path |
|---|---|
| Registrar novo artifact (criar schema) | `POST /groups/{groupId}/artifacts` |
| Buscar/match artifact por conteúdo bruto (envia o schema no body, registry retorna o(s) artifact(s) equivalente(s)) | `POST /search/artifacts` |
| Buscar artifacts por metadata (nome, labels, etc.) | `GET /search/artifacts` (filtros como `artifactId`, `groupId`, `labels`) |
| Buscar versões | `GET /search/versions` (filtros `name`, `description`) |
| Listar versões de um artifact | `GET /groups/{groupId}/artifacts/{artifactId}/versions` |
| Obter conteúdo de uma versão específica | `GET /groups/{groupId}/artifacts/{artifactId}/versions/{version}/content` |
| Obter forma canônica do conteúdo | `GET /groups/{groupId}/artifacts/{artifactId}/versions/{version}/content?canonical=true` |
| Canonicalizar conteúdo arbitrário (sem persistir) | `POST /content/canonicalize?artifactType={type}` |
| Listar referências de uma versão | `GET /groups/{groupId}/artifacts/{artifactId}/versions/{version}/references` |
| Export completo do registry | `GET /admin/export` (zip) |
| Import completo do registry | `POST /admin/import` (zip) |

Endpoints de regras — ver seção 3.

---

## 3. Regras de compatibilidade — configuração

Fonte: `https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-registry-compatibility-modes.html` e `.../assembly-rule-reference.html`, cruzado com o OpenAPI spec.

### Valores possíveis do rule type `COMPATIBILITY`
`NONE`, `BACKWARD`, `BACKWARD_TRANSITIVE`, `FORWARD`, `FORWARD_TRANSITIVE`, `FULL`, `FULL_TRANSITIVE` — os mesmos 7 modos do Confluent Schema Registry, mesma semântica (transitive = compara contra todas as versões anteriores, não só a mais recente).

Outros rule types existentes (não é só compatibilidade): `VALIDITY` (sintaxe/semântica do schema) e `INTEGRITY` (integridade de referências entre artifacts).

### Onde configurar — **discrepância entre a doc em prosa e a REST API real**
A página de compatibilidade (prosa) descreve **3 níveis** de precedência: artifact-specific > group-specific > global. Porém, ao inspecionar a spec OpenAPI v3 diretamente, **não existe endpoint de group-level rules**:

- `POST /admin/rules` — cria regra **global** (`operationId: createGlobalRule`, body `CreateRule` com campos de rule type + config)
- `GET/POST /admin/rules`, `GET/PUT/DELETE /admin/rules/{ruleType}` — CRUD de regras globais
- `POST /groups/{groupId}/artifacts/{artifactId}/rules`, `GET/PUT/DELETE .../rules/{ruleType}` — CRUD de regras **por artifact**
- Busca por `GroupRule`/`createGroupRule` no texto completo do `openapi.json`: **zero matches**.

**Não confirmado / marcar explicitamente:** não foi possível confirmar se "group-level rules" é (a) uma feature real de uma versão diferente da doc, (b) terminologia da doc que na prática se resolve através de outro mecanismo (ex.: rules por grupo aplicadas na criação do grupo, fora do escopo do `openapi.json` consultado), ou (c) imprecisão da própria documentação 3.3.x. Tentativas: leitura da página de compatibilidade, leitura da spec OpenAPI completa via busca textual por `GroupRule`/`group rule`/`createGroupRule`. Recomendo, se isso for decisão de projeto, testar direto contra uma instância rodando (`GET /apis/registry/v3` ou console) antes de depender de rules por grupo.

Confirmado sem ambiguidade: **artifact-level rule sobrepõe global rule** quando ambas configuradas (prioridade documentada e consistente com o padrão CRUD por artifact vs `/admin`).

**Relevância pro projeto:** ADR `0004` decidiu 7 tópicos com schema próprio, sem union — nesse desenho, regra de compatibilidade por artifact (schema) é suficiente; não há necessidade de rule por grupo mesmo que existisse.

---

## 4. Múltiplos schemas por tópico Kafka (equivalente a `TopicRecordNameStrategy`)

Fonte: `https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-using-kafka-client-serdes.html` e `.../assembly-configuring-kafka-client-serdes.html`.

Sim, suporte nativo via **artifact resolver strategies**, pacote `io.apicurio.registry.serde.strategy`:

| Strategy (classe) | Resolve artifactId a partir de | Permite múltiplos schemas por tópico? |
|---|---|---|
| `TopicIdStrategy` | nome do tópico + sufixo `key`/`value` (**default**) | Não — 1 schema por tópico |
| `SimpleTopicIdStrategy` | só o nome do tópico | Não |
| `RecordIdStrategy` | nome completo (full name) do schema/record | Sim |
| `TopicRecordIdStrategy` | nome do tópico **+** nome completo do schema | Sim — é o equivalente funcional ao `TopicRecordNameStrategy` do Confluent Schema Registry citado na ADR `0004` |

**Nota de nomenclatura:** o nome exato da classe Apicurio é `TopicRecordIdStrategy`, não `TopicRecordNameStrategy` (esse último é nome da classe no Confluent Schema Registry). Mesmo conceito, nomes diferentes entre os dois produtos — se a ADR `0004` ou qualquer spec futura referenciar a classe por nome, usar `TopicRecordIdStrategy` para Apicurio.

### Configuração
Propriedade: **`apicurio.registry.artifact-resolver-strategy`**, valor = FQCN da strategy, ex.:
```
apicurio.registry.artifact-resolver-strategy=io.apicurio.registry.serde.strategy.TopicRecordIdStrategy
```
Aplica-se ao **serializer** (produtor decide o artifactId ao registrar/resolver); segundo a doc, essa config não se aplica ao deserializer (que resolve via o ID/coordenadas gravadas na própria mensagem, não recalculando a partir do tópico).

**Relevância pro projeto:** ADR `0004` rejeitou deliberadamente o tópico único + union type, optando por 7 tópicos com schema 1:1. Como cada tópico já tem exatamente 1 schema, `TopicIdStrategy` (default) já resolve — `TopicRecordIdStrategy` só seria necessário se a decisão da ADR `0004` fosse revertida no futuro.

---

## 5. Exemplo oficial de uso em pipeline CI/CD

**Não existe exemplo first-party de uso do Maven plugin ou da REST API em pipeline de CI/CD de um projeto cliente.** Confirmado por dois caminhos:

1. Inspeção do diretório `.github/workflows` do próprio repositório `Apicurio/apicurio-registry` (`https://github.com/Apicurio/apicurio-registry/tree/main/.github/workflows`) — todos os 34 workflows existentes automatizam o **build/release/teste do próprio Apicurio Registry** (unit tests, integration tests, release de imagens Docker, publicação Maven Central, SDKs Go/Python/TS, etc.). Nenhum demonstra um projeto cliente rodando `mvn apicurio-registry:register` ou chamando a REST API como gate de CI/CD.
2. Busca na doc oficial (`assembly-managing-registry-artifacts-maven.html`) não retornou seção de "CI/CD example" ou GitHub Actions/GitLab CI — só a configuração do plugin em si (independente de pipeline).

Não achei também exemplo genérico ("generic CI") no restante da doc consultada.

**Conclusão:** a integração CI/CD é possível (Maven plugin + goal `test-update`/`register`, ou chamada direta à REST API), mas fica por conta de quem integra escrever os steps do pipeline (Woodpecker `.woodpecker.yml`, ADR `0007` já nota que isso ainda não foi escrito) — não há um exemplo "copia e cola" oficial do Apicurio.

---

## Resumo de gaps / não confirmados

- Group-level compatibility rules: mencionadas em prosa na doc, sem endpoint correspondente na REST API v3 spec inspecionada. Ver seção 3.
- Gradle plugin oficial equivalente ao Maven plugin: não pesquisado (fora do escopo das 5 perguntas), mas relevante dado que o stack canônico do usuário é Gradle Kotlin DSL, não Maven.
- Detalhamento completo dos valores possíveis de `ifExists` no goal `register` do Maven plugin: só `FAIL` foi confirmado no exemplo da doc; outros valores mencionados como existentes mas não enumerados na página consultada.
- CLI oficial (`apicurio-registry-cli`), mencionado en passant no README do GitHub, não investigado — fora do escopo das 5 perguntas.
