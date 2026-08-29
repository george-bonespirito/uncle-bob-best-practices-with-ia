# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

**This repo mixes two unrelated things.** Domain docs live under `app/`, not the repo root — the repo root also has `doc/transcripts/` (the source video transcript this project started from, and the script that cleans it) and `CLAUDE.md`, neither of which is part of the Order Processing domain. Skills should treat `app/` as the domain root, not the git repo root.

## Before exploring, read these

- **`app/CONTEXT-MAP.md`** — multi-context map for the Order Processing domain. Points at one `CONTEXT.md` per context.
- **`app/docs/adr/`** — system-wide decisions (span multiple contexts/microservices).
- **`app/<context>/docs/adr/`** — context-scoped decisions (e.g. `app/order/docs/adr/`, `app/notifications/docs/adr/`).

If any of these files don't exist for a given context, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved. As of this writing, only the `order` context has a `CONTEXT.md` — the other 8 microservices (Customers, Products, Pricing, Stock, Contabil, Invoice, Route, Notifications) are named in `app/CONTEXT-MAP.md` but don't have one yet.

## File structure

```
/
├── CLAUDE.md
├── doc/transcripts/            ← unrelated content pipeline, not domain — ignore
└── app/
    ├── CONTEXT-MAP.md
    ├── docs/
    │   ├── adr/                ← system-wide decisions
    │   ├── diagrams/           ← C4 diagrams, project roadmap
    │   └── specs/              ← PRD, integration specs
    ├── order/
    │   ├── CONTEXT.md
    │   └── docs/adr/           ← Order-context decisions
    └── notifications/
        └── docs/adr/           ← Notifications-context decisions (no CONTEXT.md yet)
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `app/<context>/CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR order/0001 (optimistic locking for Order writes) — but worth reopening because…_
