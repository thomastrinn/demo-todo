## Why

`GET /api/todos` filters by priority, tag, done flag, and a due window, but it offers no
way to find an item by its text. Once a list holds a few hundred items, a user who
remembers only a word of the title has to page through everything, and a client has to
pull the whole list and search it in memory. The two things belong together: a user
searches for "invoice" and then narrows the hits to the open, high priority ones.

## What Changes

- `GET /api/todos` accepts an optional `q` parameter. The API returns the items whose
  title or description matches the search text.
- The match runs on a PostgreSQL full-text index, not on a substring scan. A search for
  `invoice` matches "Invoice" in any position of either field. The text query supports a
  quoted phrase, an `or` between two words, and a leading `-` that excludes a word.
- The title weighs more than the description in the ranking, so a title hit sorts above
  a description hit.
- `sort` gains the value `relevance`. When a request holds `q` and no `sort`, the API
  orders the hits by relevance, best first. A request that asks for `relevance` without
  `q` is invalid.
- `q` combines with every existing filter — `priority`, `tag`, `done`, `dueBefore`, and
  `dueAfter` — and with every existing sort field. The API returns only the items that
  match the search text **and** every filter.
- A blank `q` behaves as if the request omits it.
- The schema gains a generated `tsvector` column over the title and the description,
  plus a GIN index on it.

No change is breaking. `q` is optional, `relevance` is a new sort value, and no existing
request or response shape changes.

## Capabilities

### New Capabilities

None. The change extends the existing capability.

### Modified Capabilities

- `todo-items`: the list requirement gains the `q` parameter, defines how the search
  text matches the title and the description, defines how the search combines with the
  filters, and gains the `relevance` sort value with the new default order for a search.

## Impact

- Code: `hu.wbstr.todo.item` — `TodoItemListParams`, `TodoItemQuery`,
  `TodoItemQueryRepositoryImpl`, `TodoItemController`. No change to `TodoItem`,
  `TodoItemService`, or the response records.
- API: `GET /api/todos` only. The change is additive.
- Database: a Liquibase changeset adds the generated `search_vector` column and its GIN
  index to `todo_item`. The column is invisible to the Spring Data JDBC mapping.
- Tests: `TodoItemQueryRepositoryTest`, `TodoItemControllerTest`, and `TodoItemApiTest`.
- Dependencies: none. The search uses the built-in PostgreSQL text search.
