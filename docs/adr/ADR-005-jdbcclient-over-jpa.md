# ADR-005: repositories use JdbcClient and explicit SQL

- **Status:** accepted

## Context

The schema is state-machine heavy and uses PostGIS `geography` columns. Mapping those through JPA needs hibernate-spatial and JTS, and the critical operation in this product is a conditional `UPDATE ... WHERE state = :expected`, which ORMs obscure.

## Decision

Repositories use Spring's `JdbcClient` with hand-written SQL. Spring Data JPA stays on the classpath but is unused for now.

## Consequences

Easy: PostGIS functions, conditional updates, and reading exactly what the database does. Hard: no free change tracking or lazy loading; every query is written by hand. If entity graphs ever get complicated, revisit — but the invariant that two seekers cannot hold one bed is worth more than ORM convenience.
