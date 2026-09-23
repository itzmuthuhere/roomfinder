# RoomFinder documentation

Monthly PG booking for Chennai. Seekers see real-time bed availability, book a visit, and move in. Owners run bed states, rent register and notices in the same system, which is what keeps availability true.

| Section | What lives here | Written by |
| --- | --- | --- |
| Product | Scope, decisions, what is deliberately out | Hand-written |
| Features | One note per shipped feature: flow, endpoints, screens, tests | Hand-written at merge |
| API | Endpoint reference, request/response, error codes | **Generated** from springdoc |
| Database | Tables, columns, relationships, ERD | **Generated** by tbls from migrations |
| Frontend | Screens, data fetching, state rules | Hand-written |
| Operations | Environments, secrets policy, deployment, runbook | Hand-written |

Rules:

1. Generated pages are never edited by hand. Fix the code or the annotation instead.
2. A pull request that changes `api/src` or `app/src` without touching `docs/` fails CI.
3. Never put a real key, token, password or customer phone number in this site. Secrets are documented by *name and location only*.
