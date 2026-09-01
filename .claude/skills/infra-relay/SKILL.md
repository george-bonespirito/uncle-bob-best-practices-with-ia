---
name: infra-relay
description: Protocol for coordinating with the claude-infra cross-session (bonespirito-cluster-k3s-infra) via SendMessage — always resolve the current session name via ListAgents first, never route the user to contact it directly, relay proposals as coming from the user. Load before any SendMessage to the infra session or before answering questions about that protocol.
user-invocable: false
---

# infra-relay

The user (George) works with two Claude Code sessions on this project: this one (application design, `uncle-bob-best-practices-with-ia`) and a separate infra session (`bonespirito-cluster-k3s-infra`, cluster k3s). Full history: memory `reference-two-agent-collaboration`.

## Always resolve the target fresh

The infra session's name/ref in `ListAgents` **changes on every handoff** — a session ends, a new one starts with a new name. Never reuse a name/ref from memory or from earlier in the conversation without calling `ListAgents` again first. A stale name either fails to resolve or, worse, silently resolves to the wrong thing if a new agent reused the name.

## This session is the single point of contact

The user should not need to open the infra session themselves. When infra work is needed:

1. Confirm the current session name via `ListAgents`.
2. `SendMessage` the proposal or request, framed as coming from the user — e.g. "George pediu X" — not as this session's own initiative when the user actually asked for it.
3. Report back when the response arrives. Never fabricate or predict what the infra session will do — wait for the actual reply.

**Never tell the user to go talk to the infra session directly.** That was tried once and corrected explicitly by the user — the whole point of the two-session split is that the user only has to talk to one of them.

Exception: the user *can* talk to it directly if they choose to (they have that session too) — that's their call, not an error to flag or discourage. If they mention having done so, just sync state, don't correct them.

## Proactive pushes from infra

The infra session now messages this one on every push to its repo, saying explicitly when nothing affects the application side. Only send a reply if something on the application side actually depends on the change (it becomes a proposal, same as any other cross-session ask) — a pure FYI needs no response.

## Domain boundary

Never write/commit to the infra repo directly (including the local read-only clone at `/Users/gbonespirito/Development/bonespirito-cluster-k3s-infra`, kept for reading current state only) — that repo is the infra session's domain. Infra-consequential decisions from this side go over as proposals via `SendMessage`, execution is its call.
