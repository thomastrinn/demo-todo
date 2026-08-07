## 1. Query and repository

- [x] 1.1 Add `int limit` and `int offset` components to `TodoItemQuery`, and add
      the constants `DEFAULT_LIMIT = 20`, `MIN_LIMIT = 1`, `MAX_LIMIT = 100`, and
      `DEFAULT_OFFSET = 0`
- [x] 1.2 Add the record `TodoItemPage<T>(List<T> items, long total)` to the
      `hu.wbstr.todo.item` package
- [x] 1.3 Change `TodoItemQueryRepository.search` to return `TodoItemPage<TodoItem>`
- [x] 1.4 In `TodoItemQueryRepositoryImpl`, append `LIMIT :limit OFFSET :offset` to the
      page statement, bound as named parameters, after the existing `ORDER BY`
- [x] 1.5 In `TodoItemQueryRepositoryImpl`, add a count statement
      `SELECT COUNT(*) FROM todo_item` that reuses the same `where(...)` builder and the
      same parameter map, without the order clause and without the paging clause
- [x] 1.6 Return the items of the page and the count as a `TodoItemPage`, and keep the
      early return that skips the tag query when the page holds no item, with the count
      still in the result

## 2. Service

- [x] 2.1 Change `TodoItemService.findAll` to return `TodoItemPage<TodoItemWithImages>`
- [x] 2.2 Keep the single image query over the identifiers of the page, and keep the
      empty-page short circuit, so an empty page returns the total with an empty item
      list

## 3. HTTP layer

- [x] 3.1 Add `@Min(1) @Max(100) Integer limit` and `@Min(0) Integer offset` to
      `TodoItemListParams`
- [x] 3.2 Add the record
      `TodoItemPageResponse(List<TodoItemResponse> items, int limit, int offset, long total)`
      with a static factory that maps a `TodoItemPage<TodoItemWithImages>`
- [x] 3.3 In `TodoItemController.toQuery`, resolve the defaults `20` and `0` for a null
      `limit` and a null `offset`, next to the existing default sort resolution
- [x] 3.4 Change `TodoItemController.findAll` to return `TodoItemPageResponse`, with the
      resolved `limit` and `offset` in the body
- [x] 3.5 Check that no new exception type is needed, because the bind step of the
      validated parameter record already returns a 400 `ProblemDetail`
- [x] 3.6 Add a handler for `MethodArgumentNotValidException` to
      `TodoItemExceptionHandler` that names the invalid field in the detail, and set
      `@Order(Ordered.HIGHEST_PRECEDENCE)` on the advice so that it runs before the
      advice of Spring Boot
- [x] 3.7 Replace the conversion message of a binding failure, so that a numeric field
      reads `must be a number` instead of the message that names the Java type

## 4. Repository tests

- [x] 4.1 Update `TodoItemQueryRepositoryTest` for the `TodoItemPage` return type, and
      set a limit and an offset in the query helper
- [x] 4.2 Add a test that a limit returns the first items of the order
- [x] 4.3 Add a test that an offset skips the earlier matches and returns the next slice
- [x] 4.4 Add a test that the last page holds the rest of the matches
- [x] 4.5 Add a test that an offset past the last match returns an empty item list with
      the real total
- [x] 4.6 Add a test that the total counts the matches of the filter, not the stored rows
- [x] 4.7 Add a test that the paging keeps the requested sort, and a test that it keeps
      the search filter and the relevance order

## 5. Controller tests

- [x] 5.1 Update every list test of `TodoItemControllerTest` for the envelope body, so an
      assertion reads `$.items[...]` instead of `$[...]`
- [x] 5.2 Add a test that a request without `limit` and `offset` sends the defaults `20`
      and `0` to the service, through a captured `TodoItemQuery`
- [x] 5.3 Add a test that `limit` and `offset` reach the service, and that the response
      echoes them with the total
- [x] 5.4 Add tests that `limit=0`, `limit=101`, and `offset=-1` return 400 with a
      `ProblemDetail`, and that the service is not called
- [x] 5.5 Add a test that `limit=all` returns 400 with the detail `limit must be a number`
- [x] 5.6 Assert that the 400 detail of `limit` and of `offset` names the parameter

## 6. API tests

- [x] 6.1 Update every list assertion of `TodoItemApiTest` for the envelope body
- [x] 6.2 Add an end-to-end test that walks two pages of a seeded list and that the two
      pages hold no item twice
- [x] 6.3 Add an end-to-end test that the total matches the filter and stays the same
      across the pages
- [x] 6.4 Add an end-to-end test that an out-of-range paging parameter returns 400 and
      that the body names the parameter

## 7. Verification

- [x] 7.1 Run `./mvnw verify` and check that every test passes
- [x] 7.2 Run the app against the compose database, call `GET /api/todos?limit=2&offset=2`,
      and check the envelope fields against the spec
- [x] 7.3 Run `openspec validate add-item-list-paging --strict` and correct any finding
