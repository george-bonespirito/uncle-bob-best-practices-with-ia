# Issue tracker: Codeberg Issues

Issues for this repo live as **Codeberg Issues (Forgejo)** on `torgge/uncle-bob-best-practices-with-ia` — not GitHub, not GitLab.

**The repo is private.** There is no anonymous read: an unauthenticated API call returns `404`, the same as for a repo that doesn't exist. Every tracker operation — including merely listing issues — needs a credential. This is the real prerequisite; the tool used to carry it is secondary.

## Two transports, same tracker

The conventions below are written in terms of **operations**, not commands. Use whichever transport the current environment actually has:

| | `tea` CLI | Forgejo REST API |
|---|---|---|
| Available on | George's workstation (login `torgge` preconfigured) | anywhere with a token |
| Auth | already configured | `Authorization: token <TOKEN>`, scope `write:repository` |
| Good for | interactive/manual work | agent sessions, CI, the llm-server |

Neither is required by the workflow. If a session has neither, it **reads the tracker via the Codeberg web UI and asks the user to perform writes** — it does not skip the check-before-create rule (see below) on the grounds that the tracker was inconvenient to reach.

### Operation reference

`API` below is `https://codeberg.org/api/v1/repos/torgge/uncle-bob-best-practices-with-ia`.

| Operation | `tea` | API |
|---|---|---|
| List issues | `tea issues list --state open [--labels ...] [--keyword ...]` | `GET $API/issues?state=open&labels=...&q=...` |
| Read issue + comments | `tea issues <n> --comments` | `GET $API/issues/<n>` + `GET $API/issues/<n>/comments` |
| Create issue | `tea issues create --title "..." --description "..."` | `POST $API/issues` — `{"title":..., "body":...}` |
| Comment | `tea comment <n> "..."` | `POST $API/issues/<n>/comments` — `{"body":...}` |
| Add labels | `tea issues edit <n> --add-labels "..."` | `POST $API/issues/<n>/labels` |
| Assign | `tea issues edit <n> --add-assignees <user>` | `PATCH $API/issues/<n>` — `{"assignees":[...]}` |
| Close / reopen | `tea issues close <n>` / `tea issues reopen <n>` | `PATCH $API/issues/<n>` — `{"state":"closed"｜"open"}` |

Two API details to verify on first use rather than assume:

- **Labels may be sent as IDs, not names**, depending on the Forgejo version. Fetch `GET $API/labels` first and map name → id if the name form is rejected.
- **`q=` is a substring search over title and body**, not the same matcher as `tea --keyword`. When checking for duplicates, try more than one fragment before concluding nothing exists.

## Conventions

- **Repo inference**: `tea` infers the repo from cwd inside this clone; pass `--repo torgge/uncle-bob-best-practices-with-ia` when running elsewhere.
- **`tea comment` multi-line bug** (transport-specific, `tea` v0.14.1): a body containing a newline hangs indefinitely; single-line bodies post instantly. Split a multi-paragraph comment into one call per paragraph, and never retry the multi-line form. `tea issues edit --description` handles multi-line fine — the bug is specific to `comment`. **The API has no such limitation**; a multi-paragraph body in `POST .../comments` is fine.

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
