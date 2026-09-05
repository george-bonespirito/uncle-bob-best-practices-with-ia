---
name: issue-ops
description: Habits for working with this repo's Codeberg issue tracker — check-before-create, Wayfinder body conventions, and picking a transport (tea CLI or Forgejo API) for the environment at hand. Load before creating, commenting on, or editing any issue.
user-invocable: false
---

# issue ops

Source of truth for the tracker itself is `docs/agents/issue-tracker.md` — this skill layers the habits that prevent repeat mistakes on top of it.

## Check before create

**Always search the tracker for an existing issue before creating one.** A duplicate map plus three duplicate child tickets were created in this repo once by skipping this check — the map already existed, from an earlier session that had been compacted out of context.

The reasoning generalises past that one incident: **compaction erases conversation memory, not tracker state.** When this session has no recollection of a ticket, that is evidence about the session, not about the tracker. Check it fresh every time.

Search by a distinctive fragment of the title (and for a map, by the `wayfinder:map` label). Try more than one fragment — substring search misses paraphrases. If a near-duplicate turns up, **read it with its comments** before deciding whether to reuse it, comment on it, or genuinely open a new one.

## Reaching the tracker

The repo is **private**, so there is no anonymous read — every operation needs a credential. Two transports carry the same operations:

- **`tea` CLI** — on George's workstation, login already configured.
- **Forgejo REST API** — anywhere with a token (`Authorization: token <TOKEN>`, scope `write:repository`). This is the path for agent sessions on the llm-server, where `tea` is not installed.

`docs/agents/issue-tracker.md` has the operation-by-operation mapping.

**If the session has neither**, say so and ask the user to run the write — do **not** treat an unreachable tracker as licence to skip check-before-create. Creating a duplicate is worse than pausing to ask.

## Wayfinder conventions

- **Map**: one issue, label `wayfinder:map`.
- **Child ticket**: body starts with `Part of #<map>`, labelled `wayfinder:<type>` (`research` / `prototype` / `grilling` / `task`).
- **Blocking**: `Blocked by: #<n>, #<n>` near the top of the body — Codeberg/Forgejo has no dependency API, so this text line *is* the mechanism. Keep its shape exact; it is parsed, not just read.
- **Frontier**: open + unblocked (every `Blocked by` issue closed) + unassigned.
- **Claim**: assign the ticket to yourself — first write, before any other work on it.
- **Resolve**: comment the answer, close the ticket, then update the map's `Decisions so far` with a link + one-line gist.

## Transport gotcha: `tea comment` and newlines

`tea comment <n> "..."` hangs indefinitely on a body containing a newline (confirmed `tea` v0.14.1); single-line bodies post instantly. Never retry the multi-line form — it will hang again. Split a multi-paragraph resolution into one call per paragraph. `tea issues edit --description` handles multi-line fine, so the bug is specific to `comment`, not to argument passing.

This is a `tea` bug, not a tracker limitation — **the API accepts multi-paragraph comment bodies normally.**
