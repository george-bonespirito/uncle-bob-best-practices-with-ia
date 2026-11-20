# Issue tracker: GitHub Issues

Issues for this repo live as **GitHub Issues** on `george-bonespirito/uncle-bob-best-practices-with-ia`. Until 2026-11-20 they lived on Codeberg (Forgejo), which has been deleted; old `#N` links in ADRs and commits point to that tracker and no longer resolve.

## Transport: `gh` CLI

Every operation goes through `gh`, authenticated as `george-bonespirito`. The repo is public, so reads also work anonymously; writes need the authenticated CLI. If a session has no `gh` auth, it still reads and asks the user to perform writes — never skip check-before-create because writing was inconvenient.

`R=george-bonespirito/uncle-bob-best-practices-with-ia`

| Operation | Call |
|---|---|
| List issues | `gh issue list -R $R --state open --label ... --search "..."` |
| Read issue + comments | `gh issue view <n> -R $R --comments` |
| Create issue | `gh issue create -R $R --title ... --body-file file.md` |
| Comment | `gh issue comment <n> -R $R --body-file file.md` |
| Add labels | `gh issue edit <n> -R $R --add-label x` (by name; create missing ones with `gh label create`) |
| Assign | `gh issue edit <n> -R $R --add-assignee george-bonespirito` |
| Close / reopen | `gh issue close <n> -R $R` / `gh issue reopen <n> -R $R` |
| Open a PR | `gh pr create -R $R --base main --head <branch> --title ... --body-file file.md` |

`--search` uses GitHub search syntax and misses paraphrases: try more than one fragment before concluding nothing exists.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

## When a skill says "publish to the issue tracker"

Create a GitHub issue with `gh issue create`.

## When a skill says "fetch the relevant ticket"

Read the issue **with its comments**. The comments usually carry the decisions; the body alone is often the stale original framing.

## Wayfinding operations

Used by `/wayfinder`. GitHub has native sub-issues and issue dependencies, but the text-line patterns below remain the contract that `/wayfinder` parses — keep them; native links are optional extras.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: an issue whose body starts with `Part of #<map>`, labelled `wayfinder:<type>` (`research` / `prototype` / `grilling` / `task`). Once claimed, assign to the driving dev.
- **Blocking**: a `Blocked by: #<n>, #<n>` line near the top of the child body. A ticket is unblocked when every blocker listed is closed — this is parsed from text, so keep the line's shape exact.
- **Frontier query**: open issues with label `wayfinder:<type>`, minus any with an unclosed blocker referenced in the body, minus any already assigned; first in map order wins.
- **Claim**: assign the ticket to yourself — the session's first write, before any other work on it.
- **Resolve**: comment the answer, close the ticket, then append a context pointer (one-line gist + link) to the map's Decisions-so-far.
