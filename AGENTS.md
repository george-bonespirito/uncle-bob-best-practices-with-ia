# AGENTS.md

Repo-specific context for OpenCode sessions. If docs conflict with code, trust code.

## Repo anatomy — two unrelated things in one root

| Path | What | State |
|---|---|---|
| `doc/transcripts/` | ASR transcript of an Uncle Bob interview + `build.mjs` that cleans it | Done |
| `app/` | Order Processing system design (supply chain reference) | Pre-implementation design only |

**Domain work is `app/` only.** `doc/transcripts/`, `CLAUDE.md`, `docs/agents/`, and `.claude/` are unrelated. Skills scoped to `app/` should treat `app/` as root, not the repo root.

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
│   ├── adr/                 # system-wide decisions (0001..0008)
│   ├── specs/               # PRD, integration contracts
│   └── diagrams/            # C4 .drawio.svg (editável no draw.io)
├── order/
│   ├── CONTEXT.md           # Ubiquitous language (glossary)
│   ├── docs/adr/            # context-local decisions
│   └── src/test/resources/features/
├── notifications/
│   ├── docs/adr/
│   └── src/test/resources/features/
└── customers/CONTEXT.md
```

**No implementation code exists.** No `build.gradle.kts`, no Kotlin, no Dockerfile. The `.feature` files are acceptance contracts with nothing behind them yet — deliberate (spec before code). This is tracked in the PRD's section 7 ("Partes cinzentas").

## How to navigate domain design

1. `app/CONTEXT-MAP.md` — the 9 contexts and how they relate
2. `app/docs/adr/` — system-wide decisions
3. `app/<context>/CONTEXT.md` — glossary (only Order and Customers have one so far)
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

**Opening a PR needs a token**, which this environment does not have. Without one, push the branch and hand the user the compare URL git prints — Forgejo accepts `?title=…&body=…` (URL-encoded) on that URL to prefill the form.

## Issue tracker: Codeberg Issues

The repo is **private** — there is no anonymous read, and an unauthenticated API call returns `404` rather than an empty result. Every tracker operation needs a credential.

Two transports carry the same operations; the workflow depends on neither:

- **`tea` CLI** — George's workstation, login `torgge` preconfigured. **Not installed on the llm-server.**
- **Forgejo REST API** — anywhere with a token (`Authorization: token <TOKEN>`, scope `write:repository`).

Full operation mapping in `docs/agents/issue-tracker.md`.

**Before creating anything**: search the tracker for an existing issue. Maps and child tickets were duplicated once when compaction erased conversation memory — **compaction erases conversation memory, not tracker state**, so "I don't remember this ticket" is evidence about the session, not the tracker.

If a session has neither transport, ask the user to perform the write. An unreachable tracker is not licence to skip the duplicate check.

**`tea` gotcha**: `tea comment` hangs forever on a body containing a newline (v0.14.1) — one call per paragraph. The API has no such limit.

## Cross-session infra relay

This session (application design) coordinates with a separate infra session (`bonespirito-cluster-k3s-infra`) via `SendMessage`. Rules:
- **This session is the user's single point of contact** — never tell the user to talk to infra directly
- Resolve session name fresh via `ListAgents` every time (names change between sessions)
- Frame proposals as coming from the user ("George pediu X")
- Never write/commit to the infra repo directly
- When infra pushes an FYI, only reply if the app side depends on it

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

- **`.claude/skills/infra-relay/`** — protocol for messaging the infra session. Load before `SendMessage`.
- **`.claude/skills/issue-ops/`** — tracker habits (check-before-create, Wayfinder body conventions, transport choice). Load before creating, commenting on, or editing any issue.
- **`.claude/agents/gherkin-consistency-reviewer.md`** — reviews `.feature` files against ADRs and CONTEXT.md for drift. No implementation exists, so this is the only consistency check.

## Infrastructure context (design, not deployed)

System targets a k3s cluster managed by ArgoCD in a **separate private repo**. Postgres, Kafka, Apicurio Registry already running there; Conductor not yet deployed. Infra decisions live in that other repo, not here. The infra relay skill handles the handoff.