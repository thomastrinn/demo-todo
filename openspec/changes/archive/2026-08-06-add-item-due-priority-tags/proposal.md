## Why

A todo item today holds only a title, a description, and a done flag. A user cannot
record when the work is due, how urgent it is, or which area it belongs to. Without
these fields a client cannot build the ordinary todo views — "due this week", "the
high priority work", "everything tagged `work`". The list endpoint also returns every
item in an undefined order, so a client must sort and filter in memory.

## What Changes

- A todo item gains three optional fields: `dueAt`, `priority`, and `tags`.
  - `dueAt` is a timestamp with an offset. It marks the deadline of the item.
  - `priority` is one of `LOW`, `MEDIUM`, `HIGH`. It defaults to `MEDIUM`.
  - `tags` is a set of short labels. An item holds at most 20 tags.
- `POST /api/todos` accepts the three fields.
- `PUT /api/todos/{id}` accepts the three fields and overwrites them. An omitted field
  clears the value, as it already does for the description.
- `PATCH /api/todos/{id}` accepts the three fields and changes only the fields that
  the body contains.
- Every response body carries the three fields.
- `GET /api/todos` accepts filter parameters: `priority`, `tag`, `dueBefore`,
  `dueAfter`, and `done`. The endpoint returns the items that match every parameter.
- `GET /api/todos` accepts a `sort` parameter over `dueAt`, `priority`, `created`,
  `updated`, and `title`. The default order stays stable and defined.
- The schema gains three columns and one child table for the tags.

No change is breaking. Every new request field is optional, and every new query
parameter is optional.

## Capabilities

### New Capabilities

None. The change extends the existing capability.

### Modified Capabilities

- `todo-items`: the item structure gains a due timestamp, a priority, and tags. The
  create, replace, and patch requirements carry the new fields. The list requirement
  gains filter parameters and a sort parameter, and it defines the default order.

## Impact

- Code: `hu.wbstr.todo.item` — `TodoItem`, `TodoItemResponse`, `CreateTodoItemRequest`,
  `ReplaceTodoItemRequest`, `PatchTodoItemRequest`, `TodoItemService`,
  `TodoItemRepository`, `TodoItemController`. New types for the priority, the tag, and
  the list query.
- API: `POST /api/todos`, `PUT /api/todos/{id}`, `PATCH /api/todos/{id}`, and both GET
  endpoints. All changes are additive.
- Database: a Liquibase changeset adds `due_at`, `priority`, and the `todo_item_tag`
  table. The list query needs a hand-written SQL fragment, because Spring Data JDBC
  offers no dynamic query API.
- Tests: `TodoItemApiTest`, `TodoItemControllerTest`, and `TodoItemRepositoryTest`.
- Dependencies: none.
