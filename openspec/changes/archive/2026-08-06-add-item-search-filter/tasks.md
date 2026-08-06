## 1. Schema

- [x] 1.1 Create `db/changelog/changes/004-add-item-search.yaml` with an `sql` changeset
      that adds the generated column `search_vector tsvector`, built from
      `setweight(to_tsvector('simple', coalesce(title, '')), 'A')` concatenated with
      `setweight(to_tsvector('simple', coalesce(description, '')), 'B')`, `STORED`
- [x] 1.2 Add `CREATE INDEX idx_todo_item_search_vector ON todo_item USING GIN (search_vector)`
      to the same changeset
- [x] 1.3 Include the new changeset from `db.changelog-master.yaml`
- [x] 1.4 Start the app against the compose database and check that Liquibase applies the
      changeset and that a manual `SELECT search_vector FROM todo_item` returns lexemes

## 2. Query

- [x] 2.1 Add a `String searchText` component to `TodoItemQuery`, and add `relevance` to
      `SORT_FIELDS`
- [x] 2.2 Add the condition `search_vector @@ websearch_to_tsquery('simple', :q)` to the
      `WHERE` builder in `TodoItemQueryRepositoryImpl`, bound as the named parameter `q`,
      and add it only when the search text is not null
- [x] 2.3 Add the `relevance` sort expression
      `ts_rank(search_vector, websearch_to_tsquery('simple', :q))` to the sort map, so
      that a title hit ranks above a description hit through the `A` and `B` weights
- [x] 2.4 Keep the tie-break on `id` and the `NULLS LAST` behaviour of the order clause
      unchanged

## 3. Query tests

- [x] 3.1 Extend the `filter(...)` and `save(...)` helpers of
      `TodoItemQueryRepositoryTest` with a search text and a description
- [x] 3.2 Add tests for a word in the title, a word in the description, a mixed-case
      search text, and a word inside a longer title
- [x] 3.3 Add tests for two words (both needed), a quoted phrase, an `or`, and a leading
      `-`
- [x] 3.4 Add a test that a search text with no searchable word returns an empty list
- [x] 3.5 Add a test that the search does not match a tag value
- [x] 3.6 Add tests that a search combines with the priority filter, the done filter, and
      a tag with a due window
- [x] 3.7 Add a test that a `relevance` sort puts the title hit before the description hit
- [x] 3.8 Add a test that a search with an explicit `dueAt` sort keeps the search filter
      and uses the requested order

## 4. HTTP

- [x] 4.1 Add `@Size(max = 255) String q` to `TodoItemListParams`, and add `@Valid` to the
      params argument of the `GET /api/todos` handler
- [x] 4.2 In `TodoItemController.toQuery`, trim `q` and map a blank value to null
- [x] 4.3 Make the default sort depend on the request: `relevance,desc` when the request
      holds a search text, `created,desc` otherwise
- [x] 4.4 Reject `sort=relevance` with an `InvalidTodoItemSortException` when the request
      holds no search text
- [x] 4.5 Pass the search text into the `TodoItemQuery` that the handler builds

## 5. HTTP tests

- [x] 5.1 Add `TodoItemControllerTest` cases: `q` reaches the service, a blank `q` reaches
      the service as null, and a trimmed `q` loses its outer spaces
- [x] 5.2 Add `TodoItemControllerTest` cases for the default sort with `q` and without `q`
- [x] 5.3 Add a `TodoItemControllerTest` case that `sort=relevance` without `q` returns 400
      with a `ProblemDetail`
- [x] 5.4 Add a `TodoItemControllerTest` case that a 256-character `q` returns 400 with a
      `ProblemDetail`
- [x] 5.5 Add `TodoItemApiTest` cases end to end: create three items, then search by a
      word, search with a filter, search with `sort=relevance,desc`, and search with a
      blank `q`

## 6. Validation

- [x] 6.1 Run `./mvnw verify` and check that every test passes
- [x] 6.2 Run the app against the compose database and check
      `GET /api/todos?q=invoice&priority=HIGH&done=false` by hand
- [x] 6.3 Run `openspec validate add-item-search-filter --strict` and fix every finding
