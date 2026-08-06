## Context

The `hu.wbstr.todo.item` package holds a Spring Modulith module with a flat structure:
a `TodoItem` record, a `ListCrudRepository`, a `@Transactional` service, a
`@RestController`, three request records, one response record, and a `ProblemDetail`
exception handler. Spring Data JDBC maps the record to the `todo_item` table. Liquibase
owns the schema. The tests run against PostgreSQL through Testcontainers.

This change adds three fields to the item and turns `GET /api/todos` into a query
endpoint. Two constraints shape the design:

- Spring Data JDBC has no criteria API and no `Specification` support. A dynamic query
  needs hand-written SQL.
- A Java record cannot tell a JSON `null` from an absent field. The current PATCH
  handler already reads `null` as "the body omits this field".

## Goals / Non-Goals

**Goals:**

- Store a due timestamp, a priority, and a set of tags on a todo item.
- Carry the three fields through create, read, replace, and patch.
- Filter the list by priority, tag, done flag, and a due window.
- Sort the list by five fields in both directions, with a defined default order.
- Keep every existing request and response shape valid.

**Non-Goals:**

- Pagination. The list endpoint keeps returning a plain JSON array.
- A tag catalogue, tag rename, or a tag usage count. A tag is a plain string on an item.
- Free-text search over the title or the description.
- A recurring due date, a reminder, or a time zone preference per user.
- A PATCH that clears the due timestamp. See the risks below.

## Decisions

### The due timestamp is an `OffsetDateTime`

`due_at` is a `timestamptz` column, and the field is `OffsetDateTime dueAt`. This
matches `created` and `updated`, so the module keeps one time type and one JSON format.
The service truncates the value to microseconds, exactly as `now()` already does, so a
response body matches a later read.

Alternative: a `LocalDate`. It fits a paper todo list better and avoids every time zone
question, but it cannot express "today at 17:00", and it would add a second date type
to the module.

### The priority is an enum with an explicit rank

`Priority` is an enum with `LOW`, `MEDIUM`, and `HIGH`. The column is
`varchar(10) NOT NULL DEFAULT 'MEDIUM'`, so the stored value stays readable in a manual
query. Spring converts the enum by name without a custom converter, and Spring MVC
rejects an unknown query parameter value with a 400.

A sort by name gives the wrong order (`HIGH`, `LOW`, `MEDIUM`), so the list query orders
by an explicit SQL `CASE` that maps each value to a rank. The rank lives in the query
builder, next to the other sort expressions.

Alternative: a `smallint` rank column. It sorts without a `CASE`, but it makes the
stored data unreadable and it needs a converter on both sides.

### The tags live in a child table

`todo_item_tag` holds `todo_item_id` and `tag`, with a composite primary key and a
cascade delete. `TodoItem` gains `@MappedCollection(idColumn = "todo_item_id") Set<TodoItemTag> tags`,
where `TodoItemTag` is a one-field record. Spring Data JDBC owns the aggregate: on every
save it deletes the child rows and inserts the current set. This gives the replace
semantics that the spec asks for at no extra cost.

A `Set` makes a duplicate tag collapse without any code. The service maps the request
strings to `TodoItemTag` values and back.

Alternative: a `text[]` column. It needs a custom `Converter` pair for Spring Data JDBC,
and a filter by tag then needs a Postgres-only array operator.

### The list query uses `JdbcClient` in a repository fragment

`TodoItemRepository` gains a fragment interface, `TodoItemQueryRepository`, with one
method: `List<TodoItem> search(TodoItemQuery query)`. The implementation builds SQL from
a fixed set of fragments and binds every value as a named parameter. No user input ever
reaches the SQL text: the sort field maps through a fixed `Map<String, String>` of
allowed expressions, and an unknown key fails before the query runs.

The query loads the tags in a second statement over the matched identifiers, then joins
the two result sets in memory. Two statements avoid the row multiplication of a join,
and the list stays small enough that a second round trip does not matter.

A filter by tag uses an `EXISTS` subquery for each requested tag, so an item must hold
all of them. A filter by priority uses `IN`. A `NULL` due timestamp never satisfies a
comparison, so an item without a due timestamp drops out of a due window without an
extra condition.

The order clause always ends with `id`, so two items with an equal sort value keep a
stable order across two requests.

Alternative: `@Query` methods for each filter combination. Five optional filters give
too many combinations to enumerate.

### The list parameters bind to one record

`TodoItemListParams` is a request record with `List<Priority> priority`, `List<String> tag`,
`Boolean done`, `OffsetDateTime dueBefore`, `OffsetDateTime dueAfter`, and `String sort`.
Spring MVC binds it from the query string. The controller parses `sort` into a field name
and a direction, and it rejects an unknown field with the same `ProblemDetail` handler
that already serves the other 400 responses.

### Validation stays on the request records

`@Size(max = 20)` on the tag list, and `@NotBlank @Size(max = 50)` on the elements
through `List<@NotBlank @Size(max = 50) String> tags`. Jakarta Validation reports the
field path, so the existing handler already produces the right `ProblemDetail`.

## Risks / Trade-offs

- **A PATCH cannot clear the due timestamp.** A record reads an absent field and a JSON
  `null` the same way. → The spec does not promise it. A client clears the value with a
  PUT. A later change can add `JsonNullable` from `jackson-databind-nullable` if a
  client needs it. The same limit already applies to the description.
- **A save rewrites every tag row.** Spring Data JDBC deletes and re-inserts the child
  rows on each save, even when the tags did not change. → The row count per item is at
  most 20, so the write cost stays small.
- **The hand-written SQL sits outside the repository abstraction.** A column rename now
  breaks the query at runtime, not at compile time. → The API test covers every filter
  and every sort field, so a rename fails the build.
- **Two statements per list request.** → The endpoint has no pagination, so the item
  count stays modest. Pagination is the answer if that changes, not a join.
- **The default order changes from undefined to newest first.** A client that relied on
  the accidental order may see a different sequence. → The old order was never a promise,
  and the change makes the endpoint predictable.

## Migration Plan

1. Liquibase changeset `002-add-item-due-priority-tags` adds `due_at` and `priority` to
   `todo_item`, and creates `todo_item_tag`.
2. `priority` gets `DEFAULT 'MEDIUM'` and a `NOT NULL` constraint, so every existing row
   fills in without a data migration step.
3. `due_at` stays nullable, so every existing row stays valid.
4. An index on `todo_item(due_at)` and one on `todo_item_tag(tag)` support the filters.
5. Rollback: Liquibase drops the table and the two columns. No data outside these three
   places changes.

## Open Questions

None.
