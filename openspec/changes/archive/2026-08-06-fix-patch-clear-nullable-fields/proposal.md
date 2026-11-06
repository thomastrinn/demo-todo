## Why

`PATCH /api/todos/{id}` cannot clear an optional field. A client that sends
`{"description": null}` expects the description to become null. The item keeps its old
description instead. The same defect will apply to `dueAt` once
`add-item-due-priority-tags` lands.

The cause is a type limit, not a logic error. The request record is a Java record:

```java
record PatchTodoItemRequest(String title, String description, Boolean done) {}
```

Jackson writes `null` into `description` for an absent field and for an explicit JSON
`null`. `TodoItemService.patch` then reads both cases the same way:

```java
description != null ? description : existing.description()
```

So the handler treats "clear this field" as "leave this field alone". A client has one
workaround: send a full `PUT`. That forces the client to know and resend every other
field, which is the exact problem PATCH exists to solve.

This is a defect against RFC 7396 and against the ordinary meaning of a partial update.
The current spec does not promise the behaviour, so the fix needs a spec change as well
as a code change.

## What Changes

- `PATCH /api/todos/{id}` SHALL tell an absent field from an explicit JSON `null`.
- An explicit `null` for `description` SHALL clear the description.
- An explicit `null` for `dueAt` SHALL clear the due timestamp.
- An absent field SHALL keep its value, as it does today.
- An explicit `null` for a field that cannot be null — `title`, `done`, `priority`,
  `tags` — SHALL return 400 with a `ProblemDetail`. Today `null` on those fields is
  silently ignored.

No change is breaking for a client that omits a field. A client that sends an explicit
`null` today gets a silent no-op; after the fix it gets a cleared field or a 400.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `todo-items`: the partial update requirement gains the distinction between an absent
  field and an explicit `null`, and it defines the result for both cases on every
  optional and every mandatory field.

## Impact

- Code: `hu.wbstr.todo.item` — `PatchTodoItemRequest`, `TodoItemService.patch`,
  `TodoItemController.patch`.
- Dependency: the fix needs a wrapper type that carries the absent state. The likely
  choice is `org.openapitools:jackson-databind-nullable` with `JsonNullable<T>`, plus
  the Jackson module registration. The design phase decides between that and a
  hand-written wrapper.
- API: `PATCH /api/todos/{id}` only. No other endpoint changes.
- Tests: `TodoItemApiTest` and `TodoItemControllerTest`.
- Sequencing: this change depends on `add-item-due-priority-tags`, because the `dueAt`
  field does not exist yet. Apply that change first.
