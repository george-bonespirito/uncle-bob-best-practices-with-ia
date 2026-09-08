---
name: infra-relay
description: Protocol for coordinating with the infra session (bonespirito-cluster-k3s-infra) over the mcp-bus MCP barramento — poll the mailbox before closing any infra-consequential decision, never route the user to contact infra directly, relay proposals as coming from the user. Load before sending to or polling the infra session, or before answering questions about that protocol.
user-invocable: false
---

# infra-relay

> **Quem é quem, quem decide o quê:** [`docs/agents/multi-session.md`](../../../docs/agents/multi-session.md) e [`docs/adr/0002`](../../../docs/adr/0002-george-atribui-identidade-e-role-das-sessoes.md). George atribui `session_id` e role; esta skill não repete o registro. O contrato do servidor é `mcp-bus/PROTOCOL.md`, no repo de infra.

The user (George) works this project across several agent sessions, split by repo: the application side (`uncle-bob-best-practices-with-ia`, this one) and the infra side (`bonespirito-cluster-k3s-infra`, cluster k3s). Full history: memory `reference-two-agent-collaboration`.

The split is by repo, not by session count — each side has had more than one window open at a time, in more than one CLI. Read the roster (`list_sessions`) instead of assuming who is there; it changes mid-session.

## Transport: the mcp-bus barramento

The two sides talk over **mcp-bus** — an MCP server (Streamable HTTP) at `https://mcp.lab.bonespirito.com/mcp`, auth by `Authorization: Bearer <token>`. It exists because `SendMessage`/`ListAgents` is Claude Code-only and the infra side is not always on Claude Code — when it ran under OpenCode, that channel stopped existing on its end.

**Which CLI is on the other side changes; do not hard-code it.** The infra side has run under OpenCode and under Claude Code, sometimes both at once in different windows. When a Claude Code infra session is up, `SendMessage` works again as a second channel alongside the bus — `ListAgents` shows whether one is live right now. The bus is the channel that works either way, which is why it stays the primary one.

Registered in this project at **local scope** (`~/.claude.json`, not in Git — the repo is public). Token lives at `~/.config/mcp-bus/token` (mode 600) and in Vault at `secret/bonespirito/mcp-bus`. Never commit it, never put it in `.mcp.json`.

**The grouping key is the squad, not the repo.** Every tool takes `squad`; `repo` is optional metadata saying which repository a session lives in or a message is about. An earlier version of this skill documented `repo` as the key and a `list_repos()` tool — both are gone.

Tools:

- `join(squad, session_id, kind, role?, repo?)`
- `list_sessions(squad)`
- `list_squads()`
- `send(squad, from, to, body, repo?)` — `to` is **required**
- `poll(squad, session_id, claim?)`
- `leave(squad, session_id)` — added by infra ADR-012
- `peek(squad, session_id, include_role_queue?)` / `ack(squad, session_id, ids)` — added by infra ADR-013

This session's identity on the bus: squad **`bonespirito`**, session_id **`app-design`**, kind `claude-code`, role **`Architect`**, repo `uncle-bob-best-practices-with-ia`.

### Two Architects: driver and navigator

The roster check happens at join time, but nothing stops a second `Architect` joining later — the bus does not announce it. When one appears, the two sessions settle it between themselves, XP pairing style, without waiting on George:

- **Driver (lead)** — closes architecture decisions, writes and revises ADRs, is the app side's voice on the bus. Lead is whoever has the lower `joined_at`; ties break on lower `session_id` alphabetically.
- **Navigator (support)** — reviews, pushes back, flags risk and duplication. Does not close a decision alone and does not send proposals to the infra side in parallel; raises it with the driver first, so two sessions never decide the same thing in opposite directions.

Either may hand off at any time by announcing it in the squad, and George overrides the tiebreak whenever he wants. Announced to the squad as a standing agreement while `app-design` was the only Architect, so it applies from the moment a second one shows up.

**The role is checked at every join, not assumed.** Join as `Architect` only when all four hold: this repo, running under the Claude CLI, host `llm-server` (`hostname`), and no other `Architect` already in the squad (`list_sessions`). If any one fails — a second Architect above all — **ask George instead of picking a role**. Re-`join` fixes a wrong registration in place, without duplicating the session.

### Who is on the other side

Address the infra side as session_id **`infra`** — DM confirmed working there. Do not assume its CLI: `infra` has been an OpenCode window and is currently Claude Code. A second infra window, `opencode`, has declared itself secondary and defers to `infra`.

`app-opencode` (OpenCode, role `Developer`) sits on the **application** repo, this session's own territory. Not an `Architect`, so the driver/navigator rule above does not fire for it — but check with it before closing a decision that touches code it may be writing.

None of this is stable. Sessions join, `leave` and re-join under different names and roles mid-conversation; the orphaned `infra` join that nobody read is what motivated `leave()` in the first place. **Read `list_sessions` at the moment you need it** rather than trusting the names written here.

`poll` on a session_id that never joined returns an error asking for `join` — it does not deliver. `join` delivers the full backlog for the squad, deliberately: a message sent before joining is not lost.

## Leave on session end

**Every session should `leave` the bus when it ends** (`/exit`, or any other planned shutdown) — it mirrors `join`. Call it as the last bus action before a planned shutdown. It is hygiene, not cosmetics: a member that never polls again holds `min(last_poll)` down, and the polite prune discards only what *every* member has read, so one dead member freezes retention for the whole squad. `leave` also returns to the queue anything that member had claimed.

**`leave` + `join` is not a reconnect.** Who comes back returns with `last_poll: -1` and gets the whole backlog replayed, and `joined_at` reopens. Fine at end of session, wrong mid-work.

**It is not a reliable recovery path either.** The prune runs inside `send`, `leave` and `ack` — not inside `poll` — so a third party's `send` between your `leave` and your `join` can discard exactly what you left to re-read. Confirmed by infra against the code; harmless today only because the prune needs 2.000 messages in a squad and we are at ~60. With `peek` the whole manoeuvre is unnecessary.

Tools only become callable in the session where the MCP connection was established, so a `leave` shipped mid-session is not callable until the next one. Crash exits cannot be caught at all. If the harness ever gains a `SessionEnd` hook, wire `leave` there instead of relying on remembering it.

## Delivery is pull, not push — poll at the right moments

Nothing wakes this session when a message arrives. The trigger that matters is semantic, not temporal:

1. `poll` at the start of a session.
2. `poll` **before closing any decision with infra consequence** — writing or revising an ADR, opening a PR, finalising a spec.
3. `poll` **before every `send`** — the mailbox may already hold the answer, or something that changes what you were about to say.

**Prefer `peek` + `ack` over `poll`.** `peek` does not consume, so a parser slip or a truncated print no longer destroys the message; `ack(ids)` then acknowledges exactly what was handled. `poll` reads and acknowledges in one operation — it cost this session half of an incoming proposal and cost infra whole messages, on the same day. Reach for `poll` only where losing a message would not matter.

`poll` reads DMs and `ALL` broadcasts. Pass `claim=true` to also take unclaimed messages addressed to this session's **role** (`Architect`) off the queue — a role message stays unclaimed until some member claims it, so a plain poll will not show it.

Rule 2 is the one that earns its keep. In a single session, infra sent three messages, two of them mid-conversation; a start-of-session poll alone would have missed both. Never close an infra-consequential decision without having read the mailbox first.

**Urgent messages get duplicated over `SendMessage` while that channel exists.** The bus wakes nobody; a message only lands when the other side happens to poll. This duplication is a deliberate agreement with infra, not redundancy left behind by accident — do not "tidy it away".

## Gotchas on the bus

**`to` is required, and `send` never fails on an unknown one — read the response instead.** `to` is a session_id (DM), `ALL` (broadcast), or a Role (claim queue). The send is always accepted, deliberately: the message is stored and delivered whenever the target joins, which is what makes the backlog work. The response says whether it actually reached anyone:

- delivered — `delivered_to: ["infra"]`
- not delivered yet — `delivered_to: []` plus a non-empty `pending_for`, alongside `sessions_in_squad` / `roles_in_squad` and a warning

So a typo (`infra-session` for `infra`) is no longer silent, but it is not an error either — it parks the message for a session that may never exist; the same goes for a Role nobody holds. **Check `delivered_to` on every send**, and treat a non-empty `pending_for` as "this has not arrived". `sessions_in_squad` and `roles_in_squad` come back in the same response, so a target can be confirmed without a second call. Infra reads as `infra`.

**`delivered_to` on a Role `send` means "someone holds that role", not "it is already visible".** A Role message counts as delivered for every member holding it, yet a plain read will not return it until the recipient claims it off the queue. Confirmed by test: a send to `Architect` came back `delivered_to: ["app-design"]` immediately, and a non-claiming read returned nothing until the claiming one ran. The role queue is deliberately independent of the inbox cursor — otherwise a routine read would swallow work without executing it.

**Confirm the target with `list_sessions` before a targeted `send`.** A send can look perfect and reach nobody who is listening: `delivered_to: ["infra"]` was once returned for an orphaned member that no session was reading. That orphan came from a human rename (`infra-claude` → `infra`) that left the old name behind — a one-off decision, *not* bus behaviour, and not something `join` does on its own. Checking the roster costs one call and the send is irreversible.

`list_sessions(squad)` before the first `send` of a session is still the habit — it just stopped being the only defence.

**`leave` + `join` redelivers the whole backlog; a plain re-`join` does not.** A first `join` handing over the full history is deliberate — it is what stops a message sent before joining from being lost — and `leave` + `join` repeats it, because the membership ended and a new one began. A *re*-`join` to fix a role does **not**: measured 2026-09-08 before and after one, `last_poll` held at 39 and `joined_at` did not move.

**Changing role does not redeliver either.** Infra reproduced it on a throwaway session: join as one role, poll, join again as another, peek — cursor held, nothing replayed. Member lookup is `squad + session_id`; role is not part of the key.

Earlier in that same session two re-joins *did* replay six already-read messages, which is why this file briefly claimed the opposite. Both explanations offered since have been checked and dropped — the `f594f56` bug (that branch never touched `last_poll`), the role queue (none of the six were role-addressed), and first-entry backlog (ids 0 and 1 reached this session *twice*, which one first entry cannot explain). **Still open.** The live hypothesis is that the member record for `app-design` was lost and recreated — a database or volume swap would do it, and the bus moved in-cluster that day. Written down unresolved on purpose: three plausible causes have already been believed and abandoned here.

Either way, anything polling on a schedule should dedupe on message `id` rather than assume delivery means new: the backlog does come back on `leave` + `join`.

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
