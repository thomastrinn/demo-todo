## Context

`GET /api/todos` builds one SQL statement in `TodoItemQueryRepositoryImpl` from a
`TodoItemQuery`, maps the rows to `TodoItem`, loads the tags in a second query, and
returns a `List`. `TodoItemService.findAll` then loads the images for the whole result
in one query. `TodoItemController.findAll` maps the list to a JSON array of
`TodoItemResponse`.

Nothing bounds the result. Every match reaches the response, and the tag query and the
image query both carry the whole result set. The endpoint already supports six filters
and six sort fields, and the order always breaks a tie on the identifier.

The API has no version prefix and no other consumer inside this repository, so a change
of the response shape lands on every client at once.

## Goals / Non-Goals

**Goals:**

- Bound the size of a list response and let a client walk the matches page by page.
- Report the number of matches, so a client can render a page count.
- Keep every existing filter and sort working with the paging.
- Keep the tag query and the image query at one round trip per page.

**Non-Goals:**

- A cursor or keyset paging. See the decision below.
- A `Link` header, a `next` URL, or any other navigation aid in the response.
- Paging of the images of an item.
- A version of the endpoint that keeps the old response shape.

## Decisions

### Offset paging over cursor paging

The endpoint takes `limit` and `offset` and maps them to SQL `LIMIT` and `OFFSET`.

The list is sortable over six fields, and three of them (`relevance`, `dueAt`,
`priority`) hold ties or nulls. A cursor needs an encoded key per sort field, and the
`relevance` value depends on the search text of the request, so a cursor could not
survive a change of `q`. Offset paging works with every sort field without a token, and
it lets a client jump to any page. The cost is a scan over the skipped rows and a page
that can shift when a write lands between two requests. The list is small enough that
both costs stay low.

Alternative: keyset paging over `(sort expression, id)`. It stays correct while rows
change and it stays fast at a deep offset, but it needs a token per sort field and it
blocks a jump to an arbitrary page. Rejected for the complexity.

Alternative: `page` and `size` instead of `limit` and `offset`. The same mechanics under
another name. Rejected because `limit` and `offset` name what the SQL does, and the
endpoint already uses flat query parameters rather than the Spring Data conventions.

### An envelope response instead of a bare array

The body becomes an object with `items`, `limit`, `offset`, and `total`.

A client needs the total to know whether a next page exists. The alternative puts the
total in an `X-Total-Count` header and keeps the bare array. A header keeps every
existing client working, but it hides the paging state from a body that a client logs or
caches, and a browser client needs a CORS header to read it. The envelope states the
paging in the payload, and it leaves room for a later field without another break.

The break is explicit in the proposal and in the spec delta. The change updates every
test that reads the list response.

### The count runs as a second query

`TodoItemQueryRepositoryImpl` gains a `count` step that reuses the same `where` builder
and the same parameter map, without the `ORDER BY`, the `LIMIT`, and the `OFFSET`.

Alternative: `COUNT(*) OVER ()` as an extra column of the page query. It saves a round
trip, but it returns no row, and therefore no total, when the offset lands past the last
match. That case has to report the real total, so the window function would still need a
fallback query. Rejected.

The repository runs both statements inside the existing read-only transaction of
`TodoItemService.findAll`.

### The page travels as a record through the layers

- `TodoItemQuery` gains `limit` and `offset`.
- `TodoItemQueryRepository.search` returns `TodoItemPage<TodoItem>`, where
  `TodoItemPage<T>(List<T> items, long total)` is a record in the same package.
- `TodoItemService.findAll` returns `TodoItemPage<TodoItemWithImages>`. It loads the
  images for the items of the page only, as it does today, and it carries the total
  through.
- `TodoItemController.findAll` returns a new `TodoItemPageResponse(List<TodoItemResponse>
  items, int limit, int offset, long total)`. The `limit` and the `offset` of the
  response are the resolved values, so a request that omits them reads back the
  defaults.

Alternative: the Spring Data `Page` and `Pageable` types with a `PageImpl`. The query
repository is hand-written over `JdbcClient` and does not use a Spring Data repository
for the search, and the JSON of a `PageImpl` is verbose and unstable across versions.
Rejected.

### The defaults and the bounds live on the parameter record

`TodoItemListParams` gains `@Min(1) @Max(100) Integer limit` and `@Min(0) Integer
offset`. Both stay nullable, and `TodoItemController.toQuery` substitutes the defaults
`20` and `0`, next to the code that already resolves the default sort.

The controller already carries `@Valid` on the parameter record, so a violation raises a
bind error and returns 400. That path is the one the `@Size(max = 255)` on `q` already
uses. A value that is not an integer fails the type conversion of the same binding step
and also returns 400. No new exception type.

The default body of Spring Boot for that failure says only `Invalid request content.`,
and the spec asks for a `ProblemDetail` that names the invalid parameter.
`TodoItemExceptionHandler` therefore gains a handler for
`MethodArgumentNotValidException` that builds the detail from the first field error, as
`<field> <message>`. The advice of Spring Boot handles the same exception at the order
`0`, so the advice of this package takes `@Order(Ordered.HIGHEST_PRECEDENCE)` to run
first.

A constraint violation carries a message that reads as a rule, so the detail keeps it:
`limit must be greater than or equal to 1`. A conversion failure carries a message that
names the Java type, so the handler replaces it. It reads the required type from the
`TypeMismatchException` of the error: a numeric field gives `limit must be a number`,
and every other field gives `holds a value of the wrong type`.

The handler applies to every validated request body of the package as well, so a create,
a replace, and a patch also name the invalid field. That matches the wording of those
requirements, which already ask for a `ProblemDetail` that names the invalid field.

Alternative: a handler that names the field for the list parameters only, and that
leaves the body endpoints on the default detail. Rejected, because it would leave two
different 400 shapes on the same resource.

## Risks / Trade-offs

- [The response shape breaks every client of `GET /api/todos`] → The proposal marks the
  break, the spec delta states the new body, and the change updates the API tests. The
  API has no released consumer outside this repository.
- [A request that omits `limit` now returns 20 items instead of every item] → This is
  the point of the change, but it is a silent behaviour change for a caller that reads
  the array length. The envelope makes the truncation visible through `total`.
- [A deep offset scans and discards the skipped rows] → Acceptable at the size of this
  list, and `limit` caps at 100 so a single page stays small. A keyset sort is the exit
  if a list ever grows past that.
- [The count and the page run as two statements, so a concurrent write can make `total`
  disagree with the page] → The two statements share a read-only transaction, and the
  drift is one item at a page boundary. A stricter isolation level costs more than the
  inconsistency does.
- [`total` needs a second scan on every list request] → The count reuses the same
  `WHERE` clause and the same indexes, including the GIN index of the search. The cost
  is one extra query per list request.

## Migration Plan

1. No database migration. The change adds no column and no index.
2. Deploy the API and the clients together, because the response shape breaks.
3. Rollback: revert the change. No data changes, so a revert needs no repair step.

## Open Questions

None.
