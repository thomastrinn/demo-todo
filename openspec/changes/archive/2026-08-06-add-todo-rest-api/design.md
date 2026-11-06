## Context

The repository holds a Spring Boot scaffold and no domain code.
`src/main/java/hu/wbstr/todo/` contains only `TodoApplication`, and
`db/changelog/db.changelog-master.yaml` is an empty list.

The stack is fixed by `pom.xml`:

- Spring Boot 4.1.0 on Java 25
- Spring Modulith 2.1.0, checked by `ModularityTests`
- Spring Data JDBC, Liquibase, PostgreSQL
- Testcontainers with `postgres:18.4-alpine`, wired by `TestcontainersConfiguration`

This change adds the first application module and the first HTTP surface. It sets the
pattern that later modules follow.

## Goals / Non-Goals

**Goals:**

- One Spring Modulith module that holds the whole todo item slice.
- A REST API for create, read, replace, partial update, and delete.
- A schema that Liquibase manages from the first changeset.
- Tests that run against a real PostgreSQL container.

**Non-Goals:**

- Authentication and authorization.
- Pagination, sorting, and filtering of the list endpoint.
- Optimistic locking and concurrent-edit conflict handling.
- Events, an OpenAPI document, and a user interface.
- Soft delete and an audit trail.

## Decisions

### One module `hu.wbstr.todo.item`

The module holds the controller, the service, the aggregate, and the repository. The
package `hu.wbstr.todo.item` sits directly under the application package, so Spring
Modulith detects it as an application module. The types that other modules must not
use go in a nested package that Spring Modulith treats as internal.

*Alternative*: split the API and the persistence into two modules. Rejected. One
vertical slice this small does not need a module boundary inside it.

### UUID identifier that the application generates

The service creates the UUID before the insert. Spring Data JDBC decides between an
insert and an update from the ID, so a non-null ID on a new aggregate would produce a
failing `UPDATE`. The aggregate therefore implements `Persistable<UUID>` and reports
`isNew()` from a transient flag.

*Alternative*: a `bigint` identity column. Rejected by the user. A UUID hides the
record count and lets the caller learn the ID without a database round trip.

*Alternative*: a `BeforeConvertCallback` that assigns the ID. Rejected. It still needs
a new-entity signal, so `Persistable` alone is simpler.

### Immutable record aggregate

`TodoItem` is a Java record. Every change produces a new instance with a new `updated`
value. Spring Data JDBC maps records through the all-args constructor.

*Trade-off*: `Persistable` needs an extra transient field on the record, which is not
elegant on a record. The alternative is a mutable class, which loses immutability
across the whole slice.

### Separate request and response records

`TodoItemResponse`, `CreateTodoItemRequest`, `ReplaceTodoItemRequest`, and
`PatchTodoItemRequest` are separate from the aggregate. The aggregate never appears on
the HTTP boundary, so a client cannot set `id`, `created`, or `updated`.

`PatchTodoItemRequest` holds nullable fields. A null field means "no change". This
makes "clear the description" impossible through `PATCH`; `PUT` covers that case.

*Alternative*: `JsonNullable` or `Optional` fields to tell "absent" from "explicit
null". Rejected as too much machinery for this slice. The design document records the
limit.

### Timestamps set in the service

The service sets `created` and `updated` from an injected `Clock`. A `Clock` bean makes
the timestamp behaviour testable.

*Alternative*: Spring Data JDBC auditing with `@CreatedDate` and `@LastModifiedDate`.
Rejected. Auditing needs `@EnableJdbcAuditing` plus a `DateTimeProvider` for the same
control, and the explicit path is easier to read for one aggregate.

*Alternative*: database defaults and a trigger. Rejected. It splits the logic across
two layers and hides it from the tests.

### `OffsetDateTime` and `timestamptz`

The columns use `timestamptz`, and the Java fields use `OffsetDateTime`. This keeps the
offset explicit in the JSON payload. Jackson writes ISO-8601 because
`spring.jackson.serialization.write-dates-as-timestamps` stays false by default.

### Liquibase from the first changeset

`db.changelog-master.yaml` includes a new file
`db/changelog/changes/001-create-todo-item.yaml`. All later schema work follows the
same pattern. No `ddl-auto` and no `schema.sql`.

### Errors through `ProblemDetail`

A `@RestControllerAdvice` in the module maps the not-found case to 404. Spring Boot 4
already turns a validation failure into a 400 `ProblemDetail` when
`spring.mvc.problemdetails.enabled` is true, so the property goes into
`application.properties`. The service throws a `TodoItemNotFoundException` and the
advice converts it.

### Tests

- `TodoItemControllerTest` uses `@WebMvcTest` with a mocked service. It covers the
  status codes, the `Location` header, and the validation failures. It needs no
  database.
- `TodoItemRepositoryTest` uses `@DataJdbcTest` with `TestcontainersConfiguration`. It
  covers the round trip, the insert-versus-update decision, and the Liquibase schema.
- `ModularityTests` already exists and checks the new module.

## Risks / Trade-offs

- **Spring Data JDBC treats the new aggregate as an update** → the aggregate implements
  `Persistable` and a repository test asserts that a create inserts a row.
- **`PATCH` cannot clear the description** → documented above. `PUT` clears it.
- **No optimistic locking, so the last write wins** → out of scope for this change. A
  `@Version` column can come later without an API change.
- **The list endpoint returns every row** → acceptable for the first slice. Pagination
  would change the response shape, so it needs its own change.
- **`@WebMvcTest` mocks the service, so it does not prove the wiring** →
  `TodoApplicationTests` still starts the whole context against a container.
- **A new module can break `ModularityTests`** → run the test as part of the task list,
  not only at the end.

## Migration Plan

The change adds a new table and a new endpoint. Nothing existing depends on either, so
no data migration is needed. A rollback removes the module, the dependency, and the
changeset include; the `todo_item` table then stays behind as an unused table.

## Open Questions

None. The identifier scheme, the update verbs, and the validation approach are settled.
