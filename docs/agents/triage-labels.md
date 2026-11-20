# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ----------------------------------------- |
| `needs-triage`              | `needs-triage`        | Maintainer needs to evaluate this issue  |
| `needs-info`                | `needs-info`          | Waiting on reporter for more information |
| `ready-for-agent`           | `ready-for-agent`     | Fully specified, ready for an AFK agent  |
| `ready-for-human`           | `ready-for-human`     | Requires human implementation            |
| `wontfix`                   | `wontfix`             | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

These labels may not exist on the GitHub repo yet. **Check before the first triage pass** — `gh label list -R george-bonespirito/uncle-bob-best-practices-with-ia`.

Create any that are missing with `gh label create`, or through the GitHub web UI when the session has no `gh` auth (see `issue-tracker.md`). Don't rely on a label being auto-created as a side effect of applying it: that behaviour varies across Gitea/Forgejo versions and silently does nothing on some.
