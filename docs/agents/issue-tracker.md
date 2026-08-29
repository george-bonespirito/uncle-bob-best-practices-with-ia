# Issue tracker: Codeberg (via tea CLI)

Issues for this repo live as Codeberg Issues (Forgejo). Remote is `torgge/uncle-bob-best-practices-with-ia` on Codeberg — not GitHub, not GitLab. Use the `tea` CLI for all operations; login `torgge` is already configured as default (`tea login list`).

## Conventions

- **Create an issue**: `tea issues create --title "..." --description "..."`. Repo is inferred from cwd when run inside this clone; pass `--repo torgge/uncle-bob-best-practices-with-ia` explicitly if needed.
- **Read an issue**: `tea issues <index> --comments`.
- **List issues**: `tea issues list --state open` (add `--labels`, `--keyword`, `--milestones` filters as needed).
- **Comment on an issue**: `tea comment <index> "..."`.
- **Apply / remove labels**: `tea issues edit <index> --add-labels "..."` / `--remove-labels "..."`.
- **Close**: `tea issues close <index>`. **Reopen**: `tea issues reopen <index>`.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

## When a skill says "publish to the issue tracker"

Create a Codeberg issue via `tea issues create`.

## When a skill says "fetch the relevant ticket"

Run `tea issues <index> --comments`.

## Wayfinding operations

Used by `/wayfinder`. Forgejo/Codeberg has no GitHub-style native sub-issue linking or issue-dependency API exposed via `tea` — use the text-line fallback pattern directly (not as a fallback-of-a-fallback).

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `tea issues create --labels wayfinder:map --title "..."`.
- **Child ticket**: an issue with `Part of #<map>` at the top of the body, labelled `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, assign to the driving dev via `--add-assignees`.
- **Blocking**: a `Blocked by: #<n>, #<n>` line near the top of the child body (no native dependency API here). A ticket is unblocked when every blocker listed is closed.
- **Frontier query**: `tea issues list --state open --labels wayfinder:<type>`, drop any with an open blocker referenced in the body or an assignee already set; first in map order wins.
- **Claim**: `tea issues edit <n> --add-assignees <username>` — the session's first write.
- **Resolve**: `tea comment <n> "<answer>"`, then `tea issues close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
