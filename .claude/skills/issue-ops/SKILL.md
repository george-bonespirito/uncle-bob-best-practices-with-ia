---
name: issue-ops
description: Habits for working with this repo's Codeberg issue tracker — check-before-create, Wayfinder body conventions, and reaching the tracker over the Forgejo REST API. Load before creating, commenting on, or editing any issue.
user-invocable: false
---

# issue ops

Source of truth for the tracker itself is `docs/agents/issue-tracker.md` — this skill layers the habits that prevent repeat mistakes on top of it.

## Check before create

**Always search the tracker for an existing issue before creating one.** A duplicate map plus three duplicate child tickets were created in this repo once by skipping this check — the map already existed, from an earlier session that had been compacted out of context.

The reasoning generalises past that one incident: **compaction erases conversation memory, not tracker state.** When this session has no recollection of a ticket, that is evidence about the session, not about the tracker. Check it fresh every time.

Search by a distinctive fragment of the title (and for a map, by the `wayfinder:map` label). Try more than one fragment — substring search misses paraphrases. If a near-duplicate turns up, **read it with its comments** before deciding whether to reuse it, comment on it, or genuinely open a new one.

## Reaching the tracker

The repo is **private**, so there is no anonymous read — every operation needs a credential. There is one transport: the **Forgejo REST API** (`Authorization: token <TOKEN>`, scope `write:repository`; on George's workstation the token is in `CODEBERG_ACCESS_TOKEN`).

**The `tea` CLI is no longer used in this project.** It is not a fallback and not an alternative — don't reach for it, and don't reinstate it in docs.

`docs/agents/issue-tracker.md` has the operation-by-operation mapping.

**If the session has no token**, say so and ask the user to run the write — do **not** treat an unreachable tracker as licence to skip check-before-create. Creating a duplicate is worse than pausing to ask.

## Wayfinder conventions

- **Map**: one issue, label `wayfinder:map`.
- **Child ticket**: body starts with `Part of #<map>`, labelled `wayfinder:<type>` (`research` / `prototype` / `grilling` / `task`).
- **Blocking**: `Blocked by: #<n>, #<n>` near the top of the body — Codeberg/Forgejo has no dependency API, so this text line *is* the mechanism. Keep its shape exact; it is parsed, not just read.
- **Frontier**: open + unblocked (every `Blocked by` issue closed) + unassigned.
- **Claim**: assign the ticket to yourself — first write, before any other work on it.
- **Resolve**: comment the answer, close the ticket, then update the map's `Decisions so far` with a link + one-line gist.

## Long bodies

The API takes multi-paragraph bodies without trouble — a resolution comment, a map body, a PR description all go in one call. Pass the payload from a file (`curl -d @body.json`) instead of inlining it: a long body inlined in the shell is where quoting and newlines go wrong.
