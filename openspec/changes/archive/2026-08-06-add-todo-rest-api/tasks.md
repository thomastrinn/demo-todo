## 1. Build setup

- [x] 1.1 Add `spring-boot-starter-validation` to `pom.xml`
- [x] 1.2 Set `spring.mvc.problemdetails.enabled=true` in
      `src/main/resources/application.properties`
- [x] 1.3 Run `./mvnw -q test` and check that the existing tests still pass

## 2. Database schema

- [x] 2.1 Create `src/main/resources/db/changelog/changes/001-create-todo-item.yaml`
      with the `todo_item` table: `id uuid` primary key, `title varchar(255) not null`,
      `description text`, `done boolean not null default false`,
      `created timestamptz not null`, `updated timestamptz not null`
- [x] 2.2 Include the new changeset from `db/changelog/db.changelog-master.yaml`

## 3. Domain and persistence

- [x] 3.1 Create the package `hu.wbstr.todo.item`
- [x] 3.2 Add the `TodoItem` record with the six fields, implementing
      `Persistable<UUID>` with a transient new-entity flag
- [x] 3.3 Add `TodoItemRepository` as a `ListCrudRepository<TodoItem, UUID>`
- [x] 3.4 Write `TodoItemRepositoryTest` with `@DataJdbcTest` and
      `@Import(TestcontainersConfiguration.class)`: save a new item and read it back,
      then save it again and check that the row count stays at 1

## 4. Service layer

- [x] 4.1 Add a `Clock` bean to the module configuration
- [x] 4.2 Add `TodoItemNotFoundException`
- [x] 4.3 Add `TodoItemService` with `create`, `findAll`, `findById`, `replace`,
      `patch`, and `delete`. `create` generates the UUID and sets both timestamps.
      `replace` and `patch` set `updated` and keep `created`. `patch` skips the null
      fields. The read, replace, patch, and delete paths throw
      `TodoItemNotFoundException` for an unknown id.

## 5. HTTP layer

- [x] 5.1 Add the request and response records: `CreateTodoItemRequest`,
      `ReplaceTodoItemRequest`, `PatchTodoItemRequest`, `TodoItemResponse`. Annotate
      the title with `@NotBlank` and `@Size(max = 255)` on create and replace, and with
      `@Size(max = 255)` plus a blank check on patch.
- [x] 5.2 Add `TodoItemController` at `/api/todos` with `POST`, `GET` (list), `GET`
      (by id), `PUT`, `PATCH`, and `DELETE`. `POST` returns 201 with a `Location`
      header. `DELETE` returns 204.
- [x] 5.3 Add a `@RestControllerAdvice` that maps `TodoItemNotFoundException` to a 404
      `ProblemDetail`

## 6. API tests

- [x] 6.1 Write `TodoItemControllerTest` with `@WebMvcTest` and a mocked service
- [x] 6.2 Cover create: 201, the `Location` header, the response body, and the
      description-free case
- [x] 6.3 Cover the blank title on `POST`, `PUT`, and `PATCH`: 400 with a
      `application/problem+json` body
- [x] 6.4 Cover list with items and list with no item (empty array)
- [x] 6.5 Cover the 404 path on `GET`, `PUT`, `PATCH`, and `DELETE`
- [x] 6.6 Cover the patch semantics: a done-only body leaves the title and the
      description unchanged
- [x] 6.7 Cover a path variable that is not a UUID: 400

## 7. Check the whole change

- [x] 7.1 Run `./mvnw -q test`. `ModularityTests` must pass with the new module.
- [x] 7.2 Walk the full lifecycle over real HTTP against real PostgreSQL: create,
      list, read, replace, patch, delete, then read again for the 404. Covered by
      `TodoItemApiTest` (`@SpringBootTest` on a random port with a `RestClient`),
      because the sandbox blocks `curl` to localhost.
