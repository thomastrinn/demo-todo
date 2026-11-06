## 1. Schema

- [x] 1.1 Create `db/changelog/changes/002-add-item-due-priority-tags.yaml` with a
      changeset that adds `due_at timestamptz NULL` and
      `priority varchar(10) NOT NULL DEFAULT 'MEDIUM'` to `todo_item`
- [x] 1.2 Add the `todo_item_tag` table to the same changeset: `todo_item_id uuid`,
      `tag varchar(50)`, a composite primary key, and a foreign key to `todo_item(id)`
      with `ON DELETE CASCADE`
- [x] 1.3 Add an index on `todo_item(due_at)` and an index on `todo_item_tag(tag)`
- [x] 1.4 Include the new changeset from `db.changelog-master.yaml`

## 2. Domain

- [x] 2.1 Add the `Priority` enum with `LOW`, `MEDIUM`, and `HIGH`
- [x] 2.2 Add the `TodoItemTag` record with one `tag` field
- [x] 2.3 Add `dueAt`, `priority`, and a `@MappedCollection(idColumn = "todo_item_id")`
      `Set<TodoItemTag> tags` to `TodoItem`
- [x] 2.4 Extend `TodoItemRepositoryTest`: save an item with a due timestamp, a
      priority, and two tags, then read it back and check all three
- [x] 2.5 Add a repository test that a save with a new tag set replaces the old rows
- [x] 2.6 Add a repository test that a delete removes the child rows

## 3. Service

- [x] 3.1 Extend `create` with the due timestamp, the priority, and the tags; default
      the priority to `MEDIUM` and the tags to an empty set; truncate the due timestamp
      to microseconds
- [x] 3.2 Extend `replace` so that an omitted due timestamp clears the value, an
      omitted tag list clears the tags, and an omitted priority becomes `MEDIUM`
- [x] 3.3 Extend `patch` so that each of the three fields changes only when the request
      holds it, and a tag list replaces the tags
- [x] 3.4 Add a mapper between the request tag strings and `TodoItemTag`

## 4. List query

- [x] 4.1 Add the `TodoItemQuery` record: the priorities, the tags, the done flag, the
      due window, the sort field, and the sort direction
- [x] 4.2 Add the `TodoItemQueryRepository` fragment interface with `search(TodoItemQuery)`
- [x] 4.3 Implement the fragment with `JdbcClient`: build the `WHERE` clause from named
      parameters, use `IN` for the priorities, and an `EXISTS` subquery for each tag
- [x] 4.4 Map the sort field through a fixed map of allowed SQL expressions; order the
      priority by an explicit `CASE` rank; put a null due timestamp last; always end the
      order clause with `id`
- [x] 4.5 Load the tags for the matched identifiers in a second statement and join the
      two result sets in memory
- [x] 4.6 Make `TodoItemRepository` extend the fragment, and route
      `TodoItemService.findAll` through `search`
- [x] 4.7 Add repository tests for each filter, for two filters together, and for each
      sort field in both directions

## 5. HTTP

- [x] 5.1 Add `dueAt`, `priority`, and `tags` to `CreateTodoItemRequest`,
      `ReplaceTodoItemRequest`, and `PatchTodoItemRequest`, with
      `@Size(max = 20) List<@NotBlank @Size(max = 50) String> tags`
- [x] 5.2 Add the three fields to `TodoItemResponse` and to `TodoItemResponse.of`
- [x] 5.3 Add the `TodoItemListParams` record and bind it on the `GET /api/todos`
      handler
- [x] 5.4 Parse the `sort` parameter into a field and a direction; reject an unknown
      field and an unknown direction with a 400
- [x] 5.5 Check that an unknown `priority` query value and an unparsable `dueBefore`
      value produce a `ProblemDetail` with status 400 through the existing handler

## 6. API tests

- [x] 6.1 Create with the three fields and read the item back
- [x] 6.2 Create without them and check the null due timestamp, the `MEDIUM` priority,
      and the empty tag list
- [x] 6.3 Replace clears the due timestamp and the tags, and resets the priority
- [x] 6.4 Patch each of the three fields on its own and check the other two stay
- [x] 6.5 Patch with an empty tag list clears the tags
- [x] 6.6 Filter by priority, by two priorities, by tag, by two tags, by the done flag,
      and by a due window
- [x] 6.7 Check that an item without a due timestamp drops out of a due window
- [x] 6.8 Sort by `dueAt` with the null values last, and sort by `priority` in both
      directions
- [x] 6.9 Check the default order is the created timestamp, newest first
- [x] 6.10 Reject an unknown priority, an unknown sort field, a blank tag, a tag above
      50 characters, and a list above 20 tags
- [x] 6.11 Check that a duplicate tag collapses to one

## 7. Verify

- [x] 7.1 Run `./mvnw verify` and check every test passes
- [x] 7.2 Run `openspec validate add-item-due-priority-tags --strict`
