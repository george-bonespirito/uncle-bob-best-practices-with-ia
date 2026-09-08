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

**The grouping key is the squad, not the repo.** Every tool takes `squad`; `repo` is optional metadata saying which repository a session lives in or a message is about. An earlier version of this skill documented `repo` as the key and a `list_repos()` tool — both are gone.

Tools:

- `join(squad, session_id, kind, role?, repo?)`
- `list_sessions(squad)`
- `list_squads()`
- `send(squad, from, to, body, repo?)` — `to` is **required**
- `poll(squad, session_id, claim?)`

This session's identity on the bus: squad **`bonespirito`**, session_id **`app-design`**, kind `claude-code`, role `Architect`, repo `uncle-bob-best-practices-with-ia`. Infra is session_id `infra`, kind `opencode`, role `DEVOPS`.

`poll` on a session_id that never joined returns an error asking for `join` — it does not deliver. `join` delivers the full backlog for the squad, deliberately: a message sent before joining is not lost.

## Delivery is pull, not push — poll at the right moments

Nothing wakes this session when a message arrives. The trigger that matters is semantic, not temporal:

1. `poll` at the start of a session.
2. `poll` **before closing any decision with infra consequence** — writing or revising an ADR, opening a PR, finalising a spec.
3. `poll` **before every `send`** — the mailbox may already hold the answer, or something that changes what you were about to say.

`poll` reads DMs and `ALL` broadcasts. Pass `claim=true` to also take unclaimed messages addressed to this session's **role** (`Architect`) off the queue — a role message stays unclaimed until some member claims it, so a plain poll will not show it.

Rule 2 is the one that earns its keep. In a single session, infra sent three messages, two of them mid-conversation; a start-of-session poll alone would have missed both. Never close an infra-consequential decision without having read the mailbox first.

**Urgent messages get duplicated over `SendMessage` while that channel exists.** The bus wakes nobody; a message only lands when the other side happens to poll. This duplication is a deliberate agreement with infra, not redundancy left behind by accident — do not "tidy it away".

## Gotchas on the bus

**`to` is required, and `send` never fails on an unknown one — read the response instead.** `to` is a session_id (DM), `ALL` (broadcast), or a Role (claim queue). The send is always accepted, deliberately: the message is stored and delivered whenever the target joins, which is what makes the backlog work. The response says whether it actually reached anyone:

- delivered — `delivered_to: ["infra"]`
- not delivered yet — `delivered_to: []` plus a non-empty `pending_for`, alongside `sessions_in_squad` / `roles_in_squad` and a warning

So a typo (`infra-session` for `infra`) is no longer silent, but it is not an error either — it parks the message for a session that may never exist; the same goes for a Role nobody holds. **Check `delivered_to` on every send**, and treat a non-empty `pending_for` as "this has not arrived". `sessions_in_squad` and `roles_in_squad` come back in the same response, so a target can be confirmed without a second call. Infra reads as `infra`.

`list_sessions(squad)` before the first `send` of a session is still the habit — it just stopped being the only defence.

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
