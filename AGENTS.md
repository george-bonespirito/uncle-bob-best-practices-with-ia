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

## Issue tracker: Codeberg via `tea`

Login `torgge` pre-configured. Commands:

```bash
tea issues list --state open --labels "..." --keyword "..."
tea issues create --title "..." --description "..."
tea issues <n> --comments
tea comment <n> "<single-line body>"  # multi-line HANGS — split into separate calls
tea issues edit <n> --add-labels "..." --add-assignees "..."
tea issues close <n>
```

**Before creating**: `tea issues list --keyword "<fragment>"` to avoid duplicates (maps and child tickets were duplicated before when compaction erased conversation memory — tracker state survives compaction).

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
- **`.claude/skills/tea-issue-ops/`** — tea CLI conventions + bug workarounds. Load before any `tea issues` create/comment/edit.
- **`.claude/agents/gherkin-consistency-reviewer.md`** — reviews `.feature` files against ADRs and CONTEXT.md for drift. No implementation exists, so this is the only consistency check.

## Infrastructure context (design, not deployed)

System targets a k3s cluster managed by ArgoCD in a **separate private repo**. Postgres, Kafka, Apicurio Registry already running there; Conductor not yet deployed. Infra decisions live in that other repo, not here. The infra relay skill handles the handoff.