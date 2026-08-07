## Why

`GET /api/todos` returns every item that matches the filters, in one response. The list
grows without a bound, so the response size, the database work, and the image lookup per
page grow with it. A client that wants the first twenty items still pays for all of
them. A client also has no way to ask for the next slice, so a long list has no user
interface that scrolls or pages.

## What Changes

- `GET /api/todos` accepts two optional parameters, `limit` and `offset`. `limit` caps
  the number of items in the response, `offset` skips that many matches before the first
  returned item.
- `limit` defaults to 20 and accepts a value from 1 to 100. `offset` defaults to 0 and
  accepts 0 or more. An out-of-range or non-numeric value returns 400 with a
  `ProblemDetail` that names the parameter.
- The `ProblemDetail` of a validation failure names the invalid field, in place of the
  text `Invalid request content.` of Spring Boot. This also applies to the body of a
  create, a replace, and a patch, which every requirement of those endpoints already
  asks for.
- **BREAKING**: the response body changes from a bare JSON array to an envelope object
  with an `items` array and the paging fields `limit`, `offset`, and `total`. `total` is
  the number of matches for the filters, not the number of items in the page.
- The paging applies after the filters and after the sort, so a page holds the items of
  that slice of the sorted result.
- The slice is stable for a given request, because the existing order already breaks a
  tie on the identifier.

The parameters are optional, so an existing request keeps working, but it now returns at
most 20 items inside an envelope instead of every item in an array.

## Capabilities

### New Capabilities

None. The change extends the existing capability.

### Modified Capabilities

- `todo-items`: the list requirement gains the `limit` and `offset` parameters, defines
  their defaults, their bounds, and the rejection of an invalid value, and replaces the
  bare array response with the paged envelope that carries `items`, `limit`, `offset`,
  and `total`.

## Impact

- Code: `hu.wbstr.todo.item` — `TodoItemListParams`, `TodoItemQuery`,
  `TodoItemQueryRepositoryImpl`, `TodoItemQueryRepository`, `TodoItemService`,
  `TodoItemController`, `TodoItemExceptionHandler`, a new page record, and a new
  response record for the envelope. No change to `TodoItem` or `TodoItemResponse`.
- API: `GET /api/todos` gains the two parameters, and its response shape breaks. The
  400 body of `POST`, `PUT`, and `PATCH /api/todos` also changes, because the new
  handler names the invalid field for every validated request of the package. The
  status codes of those three endpoints stay the same.
- Database: no migration. The change adds `LIMIT`, `OFFSET`, and a count query over the
  same `WHERE` clause.
- Tests: `TodoItemQueryRepositoryTest`, `TodoItemControllerTest`, and `TodoItemApiTest`.
  Every existing test that reads the list response needs the envelope.
- Dependencies: none.
