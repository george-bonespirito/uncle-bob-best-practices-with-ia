# Issue tracker: Codeberg Issues

Issues for this repo live as **Codeberg Issues (Forgejo)** on `torgge/uncle-bob-best-practices-with-ia` — not GitHub, not GitLab.

**The repo is public: reads need no credential, writes do.** An unauthenticated `GET` on issues or pulls returns `200`; an unauthenticated `POST` returns `401`. So listing issues, reading one with its comments, and checking for duplicates all work with a bare `curl` — only creating, commenting, labelling, assigning and closing need the token.

This reverses what this file said until 2026-09-08, which was that the repo was private and every operation, reading included, needed a credential. Verified against the API that day, without a token: both this repo and `bonespirito-cluster-k3s-infra` report `private=False`.

## Transport: the Forgejo REST API

**The `tea` CLI is no longer used in this project.** Don't reach for it, don't reinstate it in docs, and don't treat its absence as a missing transport. Every operation goes through the Forgejo REST API.

Auth: `Authorization: token <TOKEN>`, scope `write:repository`. On George's workstation the token is in `CODEBERG_ACCESS_TOKEN`.

If a session has no token, it **still reads the tracker over the API** — anonymous `GET` works — and asks the user to perform writes. Never skip the check-before-create rule on the grounds that the tracker was inconvenient to reach: with anonymous read, the check itself never needs a credential.

### Operation reference

`API` below is `https://codeberg.org/api/v1/repos/torgge/uncle-bob-best-practices-with-ia`.

| Operation | Call |
|---|---|
| List issues | `GET $API/issues?state=open&labels=...&q=...` |
| Read issue + comments | `GET $API/issues/<n>` + `GET $API/issues/<n>/comments` |
| Create issue | `POST $API/issues` — `{"title":..., "body":...}` |
| Comment | `POST $API/issues/<n>/comments` — `{"body":...}` |
| Add labels | `POST $API/issues/<n>/labels` |
| Assign | `PATCH $API/issues/<n>` — `{"assignees":[...]}` |
| Close / reopen | `PATCH $API/issues/<n>` — `{"state":"closed"｜"open"}` |
| Open a PR | `POST $API/pulls` — `{"title":..., "body":..., "head":"<branch>", "base":"main"}` |

Two API details to verify on first use rather than assume:

- **Labels may be sent as IDs, not names**, depending on the Forgejo version. Fetch `GET $API/labels` first and map name → id if the name form is rejected.
- **`q=` is a substring search over title and body**, so it misses paraphrases. When checking for duplicates, try more than one fragment before concluding nothing exists.

Bodies are JSON, so multi-paragraph text is fine everywhere — pass the payload from a file (`-d @file.json`) rather than inlining a long body in the shell.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

## When a skill says "publish to the issue tracker"

Create a Codeberg issue — either transport.

## When a skill says "fetch the relevant ticket"

Read the issue **with its comments**. The comments usually carry the decisions; the body alone is often the stale original framing.

## Wayfinding operations

Used by `/wayfinder`. Forgejo/Codeberg exposes **no native sub-issue linking or issue-dependency API** — the text-line patterns below are the mechanism, not a fallback to something better.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: an issue whose body starts with `Part of #<map>`, labelled `wayfinder:<type>` (`research` / `prototype` / `grilling` / `task`). Once claimed, assign to the driving dev.
- **Blocking**: a `Blocked by: #<n>, #<n>` line near the top of the child body. A ticket is unblocked when every blocker listed is closed — this is parsed from text, so keep the line's shape exact.
- **Frontier query**: open issues with label `wayfinder:<type>`, minus any with an unclosed blocker referenced in the body, minus any already assigned; first in map order wins.
- **Claim**: assign the ticket to yourself — the session's first write, before any other work on it.
- **Resolve**: comment the answer, close the ticket, then append a context pointer (one-line gist + link) to the map's Decisions-so-far.
