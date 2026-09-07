---
name: infra-relay
description: Protocol for coordinating with the infra session (bonespirito-cluster-k3s-infra) over the mcp-bus MCP barramento — poll the mailbox before closing any infra-consequential decision, never route the user to contact infra directly, relay proposals as coming from the user. Load before sending to or polling the infra session, or before answering questions about that protocol.
user-invocable: false
---

# infra-relay

The user (George) works this project across two agent sessions: this one (application design, `uncle-bob-best-practices-with-ia`) and a separate infra session (`bonespirito-cluster-k3s-infra`, cluster k3s). Full history: memory `reference-two-agent-collaboration`.

## Transport: the mcp-bus barramento

The two sessions talk over **mcp-bus** — an MCP server (Streamable HTTP) at `https://mcp.lab.bonespirito.com/mcp`, auth by `Authorization: Bearer <token>`. It exists because `SendMessage`/`ListAgents` is Claude Code-only, and the infra session moved to OpenCode: the old channel stopped existing on that end.

Registered in this project at **local scope** (`~/.claude.json`, not in Git — the repo is public). Token lives at `~/.config/mcp-bus/token` (mode 600) and in Vault at `secret/bonespirito/mcp-bus`. Never commit it, never put it in `.mcp.json`.

Tools: `join(repo, session_id, kind)`, `list_sessions(repo)`, `list_repos()`, `send(repo, from, to?, body)`, `poll(repo, session_id)`.

This session's identity on the bus: repo `uncle-bob-best-practices-with-ia`, session_id **`app-design`**.

`poll` on a session_id that never joined returns an error asking for `join` — it does not deliver. `join` delivers the full backlog for the repo, deliberately: a message sent before joining is not lost.

## Delivery is pull, not push — poll at the right moments

Nothing wakes this session when a message arrives. The trigger that matters is semantic, not temporal:

1. `poll` at the start of a session.
2. `poll` **before closing any decision with infra consequence** — writing or revising an ADR, opening a PR, finalising a spec.

Rule 2 is the one that earns its keep. In a single session, infra sent three messages, two of them mid-conversation; a start-of-session poll alone would have missed both. Never close an infra-consequential decision without having read the mailbox first.

## Gotchas on the bus

**`send`'s `to` is not validated against existing sessions.** A typo — `infra-session` instead of `infra` — returns `{"sent": true}` and the message goes nowhere. Infra found this in its own code review and has not fixed it yet. **Run `list_sessions` before the first `send` of a session** and address exactly what it returns. Infra reads as `infra`.

`poll` on a session_id that never joined returns an error asking for `join` — it does not deliver silently.

**Tools load at session start.** Registering the MCP server mid-session does not make `join`/`poll`/`send` callable in that session; they appear in the next one.

## This session is the single point of contact

The user should not need to open the infra session himself. When infra work is needed:

1. `poll` first — the answer may already be in the mailbox.
2. `send` the proposal or request, framed as coming from the user — "George pediu X" — not as this session's own initiative when the user actually asked for it.
3. Report back when the response arrives. Never fabricate or predict what infra will do — wait for the actual reply.

**Never tell the user to go talk to the infra session directly.** That was tried once and corrected explicitly by the user — the whole point of the two-session split is that the user only has to talk to one of them.

Exception: the user *can* talk to it directly if he chooses (that session is his too) — his call, not an error to flag or discourage. If he mentions having done so, just sync state, don't correct him.

## Proactive pushes from infra

Infra messages this session on every push to its repo, saying explicitly when nothing affects the application side. A pure FYI needs no reply. Only answer when something on the application side actually depends on the change — then it becomes a proposal, same as any other cross-session ask.

## Domain boundary

Never write or commit to the infra repo directly, including the local read-only clone at `/home/torgge/bonespirito-cluster-k3s-infra`, kept for reading current state only. That repo is the infra session's domain. Infra-consequential decisions from this side go over as proposals; execution is its call.
