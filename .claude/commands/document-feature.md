---
description: Write the feature note for what we just built and update the docs index
---

Using the current branch diff against main:

1. Copy `docs/features/_template.md` to `docs/features/<yyyy-mm-dd>-<slug>.md`.
2. Fill every section from the actual diff — do not invent behaviour that is not in the code.
   - Why: one paragraph, user problem.
   - How it works: a mermaid diagram of the real flow plus 3–6 sentences.
   - API: table of endpoints touched, with auth and purpose. Details that the OpenAPI spec cannot express (ordering, side effects, idempotency, timing) go here.
   - Data: tables and columns touched, migration number.
   - Screens: which screens changed and their empty/loading/error states.
   - Background jobs: name, schedule, failure impact.
   - Config: key NAMES only, never values.
   - Tests: what is covered; name the test that must never be deleted.
   - Known gaps: what was deliberately left out.
3. Add a row to the table in `docs/features/index.md`, newest first.
4. If a decision was made that is expensive to reverse, also create an ADR from `docs/adr/_template.md` and add it to the ADR index.
5. Print a short summary of what you documented and anything in the diff you could not explain.

Never write a secret, token, customer phone number or real listing data into docs.
