---
name: tea-issue-ops
description: Conventions for working with this repo's Codeberg issue tracker via the tea CLI — check-before-create, Wayfinder body conventions, and the tea comment multi-line bug workaround. Load before any tea issues create/comment/edit call.
user-invocable: false
---

# tea issue ops

Source of truth for the tracker itself is `docs/agents/issue-tracker.md` — this skill layers the habits that prevent repeat mistakes on top of it.

## Check before create

**Always run `tea issues list --keyword "<fragment>"` (or `--labels wayfinder:map` for a map) before any `tea issues create`.** A duplicate map + 3 duplicate child tickets were created in this repo once by skipping this check — the map already existed from an earlier session that got compacted out of context. Compaction erases conversation memory, not tracker state — the tracker is always the source of truth, check it fresh.

If a near-duplicate title turns up, read it (`tea issues <n> --comments`) before deciding whether to reuse, comment on, or genuinely create a new one.

## Wayfinder conventions

- **Map**: one issue, label `wayfinder:map`.
- **Child ticket**: body starts with `Part of #<map>`, labelled `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`).
- **Blocking**: `Blocked by: #<n>, #<n>` near the top of the body — no native dependency API on Codeberg/Forgejo via `tea`, this is the only mechanism.
- **Frontier**: open + unblocked (every `Blocked by` issue closed) + unassigned.
- **Claim**: `tea issues edit <n> --add-assignees <username>` — first write, before any other work on the ticket.
- **Resolve**: comment(s) with the answer, then `tea issues close <n>`, then update the map's `Decisions so far` with a link + one-line gist.

## `tea comment` multi-line bug

`tea comment <index> "..."` hangs indefinitely on a body containing a newline (confirmed `tea` v0.14.1) — single-line bodies post instantly. Never retry the multi-line form. Split a multi-paragraph resolution into multiple single-line `tea comment` calls, one paragraph per call. `tea issues edit --description "..."` handles multi-line fine — the bug is specific to `comment`.
