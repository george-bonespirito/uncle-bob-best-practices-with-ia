# AGENTS.md — uncle-bob-best-practices-with-ia

Repositório do domínio Order Processing (supply chain reference) + pipeline de transcrição Uncle Bob. Código ganha de doc; se conflitar, `git log` responde.

## Estado

Domínio em `app/`: design completo, implementação iniciada (vertical slice Bulk Import em `:order`). O que existe: Gradle multi-projeto (Kotlin DSL), Spring Boot 3.3, Flyway, 11 `.feature` Gherkin (10 `@pending`), `Dockerfile` + `compose.yaml` (issue #70). O que **não** existe: cliente Kafka, Actuator, worker-polling/Conductor, porta configurada (default 8080), testes de integração Saga. Rastreador vivo: [`app/docs/specs/order-processing-prd.md` seção 7](app/docs/specs/order-processing-prd.md) ("Partes cinzentas").

Transcrição em `doc/transcripts/`: `build.mjs` limpa `transcript-1.txt` → `uncle-bob-software-fundamentals-ai.md`. Idempotente, sem dependências.

## Territórios

Dois sem relação: `app/` (domínio) e `doc/transcripts/` (pipeline ASR). Skills em `app/` tratam `app/` como raiz.

## O domínio Order Processing

9 bounded contexts em `app/`: **Order**, **Customers**, **Products**, **Pricing**, **Stock**, **Contabil**, **Invoice**, **Route**, **Notifications**. Entrada: [`app/CONTEXT-MAP.md`](app/CONTEXT-MAP.md) (relações) → cada contexto tem `CONTEXT.md` (linguagem ubíqua) + `docs/adr/` (decisões locais) + `src/test/resources/features/` (Gherkin). ADRs de sistema em [`app/docs/adr/`](app/docs/adr/). Diagramas C4 editáveis em [`app/docs/diagrams/`](app/docs/diagrams/).

Implementado hoje: `:order` (Bulk Import: `ImportOrdersController`, parser CSV, policies, Flyway V1–V4) e `:notifications` (main só tem `NotificationsApplication.kt`). 10 de 11 `.feature` em `@pending` — só `bulk_import` está fora.

## Comandos

```bash
# Transcrição
node doc/transcripts/build.mjs

# Domínio (roda no MacBook — única máquina com JVM/Gradle/Node)
docker compose -f app/compose.yaml run --rm test

# Um teste só — o comando após o nome do serviço substitui o command:
docker compose -f app/compose.yaml run --rm test ./gradlew :order:test --tests "com.bonespirito.order.domain.policy.ParsedRowTest"
```

Não há `./gradlew` na raiz; build é `docker compose -f app/compose.yaml run --rm test`. Testes rodam em H2 in-memory (`app/*/src/test/resources/application.yml`) — `check` não precisa do cluster k3s.

## Convenções que o modelo não deriva

- **ADR**: numerada por escopo (`app/docs/adr/`, `app/<ctx>/docs/adr/`). Revertida não apaga — nota datada no fim. Revisão **anexa**, não reescreve. Skill: `.agents/skills/adr-new/`.
- **Diagrama**: `.drawio.svg` no mesmo PR da decisão. Hook avisa o que ficou para trás.
- **Infra**: cluster em repo separado (`bonespirito-cluster-k3s-infra`). Coordenação via [`agent-bus`](https://github.com/george-bonespirito/agent-bus) (outbox NDJSON por papel). Skill: `.agents/skills/infra-relay/`.
- **Enforcement de contexto de vendor** (ADR-005 do agent-bus): nenhum `CLAUDE.md`/`CLAUDE.local.md` no repo, nenhuma referência viva a `CLAUDE.md` fora de `.vendor-context-allow`. Duas camadas, uma implementação (`scripts/check-vendor-context.sh`, fonte canônica no agent-bus): hook local (`scripts/hooks/pre-commit`, `--staged`) + CI (`.woodpecker/vendor-context.yaml`, `--all`). Instalar o hook **uma vez por clone**: `git config core.hooksPath scripts/hooks` — não vem com o commit.
- **Build/teste**: só no MacBook (`DEVELOPER`). `ARCHITECT` (llm-server) não compila nem testa; alegação de "funciona" sem evidência não é verificada.
- **Tracker**: GitHub Issues (público, leitura anônima). `gh` CLI. Labels mattpocock. Skill: `.agents/skills/issue-ops/`.
- **Quality gate**: `detekt` + testes + JaCoCo (LINE≥80% BRANCH≥70%) + ArchUnit + Pitest (domain.policy.*, 80%). Pitest fica **fora** do `check` (caro, de propósito) — rode `./gradlew pitest` à parte. Skill: `.agents/skills/quality-gate/`.
- **Gherkin**: `cucumber.filter.tags=not @pending` — remover a tag `@pending` do cenário é o gesto que o põe no check. Implementação de um cenário começa aí.

## Fronteiras

- Esta sessão (`dev.uncle-bob`, MacBook, `DEVELOPER` Manager) implementa, compila, testa, abre PR no domínio.
- Não escreve no repo de infra (`bonespirito-cluster-k3s-infra`) — `devops.infra` decide o como.
- Não mergeia PR (só George mergeia). PR nasce `WIP:`.
- Branch: `<tipo>/<role>/<slug>` (ex.: `feature/developer/bulk-import`).
- Commit: Conventional Commits + emoji.

## Ponteiros

- Política de time (identidade, autoridade, trabalho paralelo): [`docs/agents/multi-session.md`](docs/agents/multi-session.md)
- Tracker e triagem: [`docs/agents/issue-tracker.md`](docs/agents/issue-tracker.md)
- Domain docs: [`app/CONTEXT-MAP.md`](app/CONTEXT-MAP.md), [`app/docs/adr/`](app/docs/adr/), [`app/<ctx>/CONTEXT.md`](app/order/CONTEXT.md)
- Skills: `.agents/skills/` (fonte) + symlink `.claude/skills/`

## Vendor

`.claude/` guarda **só** hooks (`.claude/hooks/`), subagents (`.claude/agents/`), `settings.json` — vendor irredutível (Cursor importa hooks lendo `.claude/settings.json`). Skills movidas para `.agents/skills/` com symlink em `.claude/skills/`. `doc/transcripts/CLAUDE.md` virou `doc/transcripts/README.md` (documentação comum, referenciada pela raiz).