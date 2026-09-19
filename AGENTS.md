# AGENTS.md

Repo-specific context for OpenCode sessions. If docs conflict with code, trust code.

## Repo anatomy — two unrelated things in one root

| Path | What | State |
|---|---|---|
| `doc/transcripts/` | ASR transcript of an Uncle Bob interview + `build.mjs` that cleans it | Done |
| `app/` | Order Processing (supply chain reference) | Design completo; implementação começou — só a vertical slice de Bulk Import |

**Domain work is `app/` only.** `doc/transcripts/`, `CLAUDE.md`, `docs/agents/`, and `.claude/` are unrelated. Skills scoped to `app/` should treat `app/` as root, not the repo root.

## Working alongside other sessions

Several agent sessions work this project at once, across two repos and two CLIs, coordinated over the `agent-bus` (o mcp-bus foi descartado em 2026-09-19). **Before joining the bus, opening a branch or a PR, read [`docs/agents/multi-session.md`](docs/agents/multi-session.md)** — it is the single source for who is who, who decides what, and how to work in parallel without collisions.

Two rules from it that catch a session out immediately: your working identity is `role` + `kind` + `repo`, and you **receive** it — you never compute or choose it; and a PR opens as `WIP:` and is merged by George alone.

## Only executable command

```bash
node doc/transcripts/build.mjs
```

Reads `doc/transcripts/transcript-1.txt`, writes `doc/transcripts/uncle-bob-software-fundamentals-ai.md`. No `package.json`, no dependencies, pure `node:fs`. Idempotent.

## Transcript pipeline fragility (editing `build.mjs`)

- `SEG` object is **positional** (1‑based unit index). Any insert/remove in `transcript-1.txt` shifts all indices — `SEG` breaks silently.
- `REPLAC` is a list of `[regex, replacement]` specific to this transcript's ASR errors. Not reusable without review.
- `SECTION` maps time ranges (minutes) to PT-BR section headers.

## Order Processing design structure

```
app/
├── CONTEXT-MAP.md           # 9 bounded contexts + relationships
├── docs/
│   ├── adr/                 # system-wide decisions (0001..0010)
│   ├── specs/               # PRD, integration contracts
│   └── diagrams/            # C4 .drawio.svg (editável no draw.io)
├── build.gradle.kts         # Gradle Kotlin DSL; `subprojects` aplica spring-boot-starter-web a todos
├── settings.gradle.kts      # módulos com código: :order e :notifications
├── order/
│   ├── CONTEXT.md           # Ubiquitous language (glossary)
│   ├── docs/adr/            # context-local decisions
│   ├── src/main/kotlin/     # Bulk Import: domain, application, infra/{web,csv,config}
│   ├── src/main/resources/  # application.yml + db/migration (Flyway V1..V4)
│   └── src/test/resources/features/
├── notifications/
│   ├── CONTEXT.md
│   ├── docs/adr/
│   ├── src/main/kotlin/     # só NotificationsApplication.kt (sem dependências próprias)
│   └── src/test/resources/features/
└── <outros 7 contextos>/CONTEXT.md   # só spec/ADR/glossário, sem src/
```

**Existe código, e pouco.** Gradle e Kotlin existem; `Dockerfile` e `compose.yaml` não. O que está implementado é **a vertical slice de Bulk Import** em `:order` — `ImportOrdersController` (`POST /imports/orders`, `GET /imports/orders/{id}`), parser CSV, políticas de domínio e migrations Flyway. `:notifications` é só a classe de aplicação.

O que **não** existe, e costuma ser assumido como existente:

- **Nenhum cliente Kafka em lugar nenhum.** `order/build.gradle.kts` tem `spring-boot-starter-data-jpa` e `flyway-core`, e nada mais. O worker-polling/Conductor que consumiria os 7 tópicos `order.*.result` não foi escrito — é o item 10 da seção 7 da PRD, marcado **bloqueado**.
- **Actuator não é dependência**, então não há `/actuator/health`, liveness nem readiness. Registrado como pendência na ADR order/0008.
- **Porta não configurada** — `order/src/main/resources/application.yml` só declara `spring.application.name: ms-order`, logo vale o default 8080 do Boot.
- Dos 11 `.feature`, **10 estão `@pending`** (filtrados do run). Só `bulk_import.feature` tem implementação atrás.

Isto é rastreado na seção 7 da PRD ("Partes cinzentas").

## How to navigate domain design

1. `app/CONTEXT-MAP.md` — the 9 contexts and how they relate
2. `app/docs/adr/` — system-wide decisions
3. `app/<context>/CONTEXT.md` — glossary (os 9 contextos têm o seu; item 5 da seção 7 da PRD está fechado)
4. `app/<context>/docs/adr/` — context-scoped decisions
5. `app/<context>/src/test/resources/features/` — Gherkin acceptance tests

Missing `CONTEXT.md` for a context is expected — don't flag it, don't create it proactively. The domain-modeling workflow creates them lazily.

## ADR conventions

- **Never delete a superseded ADR.** Keep it with a note explaining what changed and why. Example: `order/0009` and `order/0010`.
- **Revisions are appended**, not rewritten — dated note at end of file.
- **Contradict an ADR?** Surface it explicitly rather than silently overriding.

## Commit / branch conventions

- Conventional Commits with emoji: `📚 docs:`, `🐛 fix:`, `✨ feat:`
- Branch prefix by type (`docs/`, `feature/`, `fix/`), always from `main`, never commit directly to `main`
- Squash-merge PRs, one subject per PR

### Git access from the llm-server

`origin` is SSH (`git@codeberg.org:torgge/uncle-bob-best-practices-with-ia.git`); pushes work non-interactively via a dedicated key. **HTTPS does not work here** — there is no credential helper, so an HTTPS remote fails with `could not read Username`.

**Opening a PR does not need a token** — Forgejo's AGit flow creates one over SSH:

```bash
git push origin HEAD:refs/for/main \
  -o topic="<branch-ish-name>" \
  -o title="..." \
  -o description="..."
```

Two constraints, both learned the hard way:

- **Push options must not contain newlines** (`fatal: push options must not have new line characters`) — the description has to be a single paragraph. For a body with real structure, prefer the branch + compare-URL route below.
- An AGit PR's head is `refs/pull/<n>/head`, **not** the branch. Pushing the branch separately *and* running AGit creates two overlapping PRs — pick one route per change.

The alternative, which supports a multi-line body: push the branch normally and open the compare URL git prints, with `?title=…&body=…` (URL-encoded) appended to prefill the form.

**Editing PR/issue metadata still needs a token** — reading it doesn't (see below, repo is public).

## Issue tracker: Codeberg Issues

The repo is **public since 2026-09-08** — anonymous `GET` works (listing issues, reading one, checking for duplicates), only writes (`POST`/`PATCH`) need a credential. Older docs saying "private, every operation needs a token" are stale — verified against the API without a token.

Everything goes through the **Forgejo REST API** — a token with scope `write:repository`, sent as `Authorization: token <TOKEN>` (on George's workstation: `CODEBERG_ACCESS_TOKEN`). **The `tea` CLI is no longer used here**; don't reach for it and don't put it back in docs.

Full operation mapping in `docs/agents/issue-tracker.md`.

**Before creating anything**: search the tracker for an existing issue. Maps and child tickets were duplicated once when compaction erased conversation memory — **compaction erases conversation memory, not tracker state**, so "I don't remember this ticket" is evidence about the session, not the tracker.

If a session has no token, it still reads the tracker over the API — anonymous `GET` works — and asks the user to perform writes only. With anonymous read available, the duplicate check never needs a credential, so there's no excuse to skip it.

## Cross-session infra relay

This session (application design) coordinates with a separate infra session (`bonespirito-cluster-k3s-infra`). **O transporte mudou:** o mcp-bus foi descartado e o canal do time é o repositório `torgge/agent-bus` — uma outbox NDJSON por papel, em git, escritor único por arquivo, cursor por último `id` visto ([ADR-001 de lá](https://codeberg.org/torgge/agent-bus/src/branch/main/docs/adr/ADR-001.md)). A remoção dos recursos do mcp-bus do k3s foi pedida ao DEVOPS; execução e ordem são dele.

O que não mudou, porque nunca dependeu do transporte:
- **This session is the user's single point of contact** — never tell the user to talk to infra directly
- **Architect delega o quê; DEVOPS decide o como** — implementação e o que entra no repo de infra continuam decisão dele
- Never write/commit to the infra repo directly
- Quando infra manda um FYI, só responda se o lado da aplicação depender

**Enquanto não houver runtime no `agent-bus`, a leitura é manual** e o cursor de cada leitor vive em `bus/cursors/<leitor>.json`, fora do git. A skill `infra-relay` ainda documenta o mcp-bus e **está desatualizada** — reescrita pendente, registrada na nota de 2026-09-19 em `docs/agents/multi-session.md`.

## Triage labels (mattpocock/skills convention)

| Canonical role | Label string |
|---|---|
| needs-triage | `needs-triage` |
| needs-info | `needs-info` |
| ready-for-agent | `ready-for-agent` |
| ready-for-human | `ready-for-human` |
| wontfix | `wontfix` |

## Diagrams

C4 diagrams are `.drawio.svg` — SVG with draw.io XML embedded. Open in browser, editable in draw.io. Located in `app/docs/diagrams/`.

## Skills and agents in this repo

- **`.claude/skills/infra-relay/`** — protocolo de conversa com a sessão de infra. **Desatualizada:** descreve o mcp-bus, que saiu; reescrita pendente.
- **`.claude/skills/issue-ops/`** — tracker habits (check-before-create, Wayfinder body conventions, transport choice). Load before creating, commenting on, or editing any issue.
- **`.claude/agents/gherkin-consistency-reviewer.md`** — reviews `.feature` files against ADRs and CONTEXT.md for drift. Os 10 `.feature` `@pending` não têm implementação atrás, então para eles é a única checagem possível.
- **`.claude/agents/adr-consistency-reviewer.md`** — confere uma ADR nova ou revisada contra as outras do mesmo escopo e contra os `CONTEXT.md`.
- **`.claude/agents/archunit-boundary-reviewer.md`** — violação de fronteira hexagonal/DDD em `.kt`, antes de o teste ArchUnit pegar.

## Infrastructure context (design, not deployed)

System targets a k3s cluster managed by ArgoCD in a **separate private repo**. Postgres, Kafka, Apicurio Registry already running there; Conductor not yet deployed. Infra decisions live in that other repo, not here. The infra relay skill handles the handoff.