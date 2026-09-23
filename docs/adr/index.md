# Architecture decisions

Short records of decisions that are expensive to reverse. Use `docs/adr/_template.md`, numbered in order.

| ID | Decision | Date | Status |
| --- | --- | --- | --- |
| ADR-001 | Single Spring Boot monolith, not microservices | | accepted |
| ADR-002 | Bed availability is a state machine in Postgres, never a cached counter | | accepted |
| ADR-003 | We never hold rent or deposit money | | accepted |
| ADR-004 | React PWA wrapped for Play Store, no native codebase | | accepted |
| ADR-005 | Repositories use Spring JdbcClient and explicit SQL, not JPA entities | 2026-09-22 | accepted |
