---
description: Full loop for one feature — plan, build, test, document, PR
---

Work one feature at a time, in this order. Stop and ask if a step is ambiguous.

1. **Plan.** Restate the feature in 3 lines: user, behaviour, done-when. List files you will touch. Wait for my "go" if the plan changes the schema or a bed state transition.
2. **Migrate.** If the schema changes, add a new Flyway migration. Never edit an applied one.
3. **Build.** Backend first (service + controller + DTOs), then the screen. Small diffs, feature package layout from CLAUDE.md.
4. **Test.** Testcontainers Postgres. Cover the happy path, the conflict path, and the expiry/background path. Run the full suite.
5. **Self-review.** Re-read the diff for: concurrency on bed state, money handled as paise, no entity returned from a controller, no secret in code, no new dependency without justification.
6. **Document.** Run the document-feature command.
7. **PR.** Branch `feat/<slug>`, commit message in imperative mood, PR body = what changed, why, how tested, screenshots if UI.

Report at the end: what shipped, what is not covered, what I should test by hand on a phone.
