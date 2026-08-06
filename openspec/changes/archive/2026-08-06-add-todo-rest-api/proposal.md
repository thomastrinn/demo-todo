## Why

The application is an empty Spring Boot scaffold with no domain code and no HTTP
surface. Clients need a way to keep track of todo items, so the first vertical slice
is a REST API that stores todo items and exposes them over HTTP.

## What Changes

- Add a `todo-items` capability: a todo item with a title, a description, a done flag,
  a created timestamp, and an updated timestamp.
- Add REST endpoints under `/api/todos` for create, list, read, replace, partial
  update, and delete.
- Add a `todo_item` table through a Liquibase changeset. The master changelog is empty
  today.
- Add request validation with `spring-boot-starter-validation`. The API rejects a
  blank title with 400.
- Return `ProblemDetail` bodies for the error responses (400 and 404).
- Add a new Spring Modulith application module for the todo item code.

## Capabilities

### New Capabilities

- `todo-items`: the lifecycle of a todo item and the HTTP contract that creates,
  reads, changes, and deletes it.

### Modified Capabilities

None. The project has no existing specs.

## Impact

- **Code**: new package under `src/main/java/hu/wbstr/todo/`. `TodoApplication` and
  the existing tests stay unchanged.
- **API**: new public HTTP surface at `/api/todos`.
- **Database**: new `todo_item` table. The master changelog
  (`src/main/resources/db/changelog/db.changelog-master.yaml`) gains its first
  include.
- **Dependencies**: add `spring-boot-starter-validation` to `pom.xml`.
- **Tests**: `ModularityTests` must stay green with the new module. New tests cover
  the controller and the repository. The repository tests use the existing
  `TestcontainersConfiguration` PostgreSQL container.
