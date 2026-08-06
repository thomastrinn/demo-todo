## Context

`GET /api/todos` already runs through a repository fragment,
`TodoItemQueryRepositoryImpl`, that builds SQL from a `TodoItemQuery` record with
`JdbcClient`. The fragment holds a fixed list of `WHERE` fragments, a fixed map of
allowed sort expressions, and a second statement that loads the tags of the matched
items. The store is PostgreSQL 18.4 in the compose file and in the Testcontainers
configuration. Liquibase owns the schema. Spring Data JDBC maps the `TodoItem` record to
the `todo_item` table.

This change adds one more `WHERE` fragment and one more sort expression. The open
question is not where the code goes, but how the text match works and where the index
lives.

Three constraints shape the design:

- The list endpoint returns a plain JSON array with no page limit, so the match must use
  an index. A full scan over the description column grows with the table.
- Spring Data JDBC writes an `INSERT` and a `SELECT` from the mapped record fields only,
  so a column that the record does not declare stays invisible to the mapping.
- The search must combine with the existing filters and the existing sort fields. The
  fragment already ANDs its conditions, so the search is one more condition.

## Goals / Non-Goals

**Goals:**

- Match a search text against the title and the description of an item over a GIN index.
- Rank a title hit above a description hit, and order the hits by relevance by default.
- Combine the search with every existing filter and with every existing sort field.
- Keep every existing request and response shape valid.
- Keep the SQL free of user input, exactly as the current fragment does.

**Non-Goals:**

- Search over the tags, the image file names, or any field other than the title and the
  description.
- Prefix matching or autocomplete. `invoi` does not match `invoice`.
- Fuzzy matching, a typo tolerance, or a similarity score (`pg_trgm`).
- Accent-insensitive matching. `arvíz` does not match `arviz`. That needs the `unaccent`
  extension, and an extension needs a privileged migration.
- Language-aware stemming. See the decision on the search configuration below.
- Pagination, a hit count, or a highlighted snippet of the matched text.
- A search across two capabilities. The endpoint stays `GET /api/todos`.

## Decisions

### The search uses the built-in PostgreSQL text search, not `LIKE`

The condition is `search_vector @@ websearch_to_tsquery('simple', :q)`. PostgreSQL parses
the text into lexemes, and a GIN index answers the `@@` operator. A `LIKE '%word%'` scan
cannot use a B-tree index, so it reads every row, and it also matches inside a word,
which turns `q=cat` into a hit on "communicate".

Alternative: `ILIKE '%q%'` over both columns. It is two lines of SQL and it needs no
migration, but it scans the whole table, it cannot rank a hit, and it has no phrase or
negation support. Alternative: `pg_trgm` with a similarity operator. It tolerates a typo,
but it needs an extension, and it ranks by string distance rather than by word hits.

### The `tsvector` lives in a generated column with a GIN index

The changeset adds:

```sql
ALTER TABLE todo_item ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'B')
    ) STORED;
CREATE INDEX idx_todo_item_search_vector ON todo_item USING GIN (search_vector);
```

The two-argument `to_tsvector`, `setweight`, and the `tsvector` concatenation are all
immutable, so PostgreSQL accepts them in a stored generated column. The database keeps
the column in step with the title and the description on every insert and every update,
so no application code and no trigger can let the index drift. `TodoItem` does not
declare the column, so Spring Data JDBC never reads it and never writes it.

Alternative: compute `to_tsvector(...)` inside the query. It needs no column and no
migration, but then the expression index has to match the query expression exactly, and
every row is re-parsed on every search. Alternative: a plain column plus a trigger. It
works on any PostgreSQL version, but it is more moving parts for the same result.

Liquibase gets the two statements as one `sql` changeset. Liquibase has no portable
`addColumn` attribute for a generated column, and the change is PostgreSQL-only in any
case, like the `timestamptz` and `uuid` types already in the schema.

### The search configuration is `simple`

`to_tsvector('simple', ...)` lowercases the input and splits it into words. It keeps every
word, and it does not stem. Stop word removal belongs to the dictionary, not to the name
of the configuration: the built-in `simple` dictionary carries no stop word list, while
`english_stem` carries `stopwords = 'english'`. The `english` configuration therefore
drops `the` and stems the rest, so `meetings` would match `meeting` — but it stems English
only, and it mangles other languages. A todo list holds short, mixed-language text, so a
wrong stemmer costs more than a missing one. `simple` gives one predictable rule: a whole
word matches a whole word.

Two consequences follow. A search for a common word such as `the` returns every item that
holds it, because `simple` keeps the word. A quoted phrase counts the common words as
positions, so `"pay the invoice"` matches that exact sequence and nothing else.

An empty result needs a search text that holds no lexeme at all, such as `+++`. The
scenario "The search text holds no searchable word" states that behaviour.

Alternative: `english`. Better recall on English text, wrong results on Hungarian text.
Alternative: a configuration per item, chosen from the text. That needs language
detection, which is a project of its own.

### `websearch_to_tsquery` parses the search text

`websearch_to_tsquery` never raises a syntax error. It reads a quoted phrase, an `or`
between two words, and a leading `-` as a negation, and it ANDs the rest. `plainto_tsquery`
would only AND the words, and `to_tsquery` demands valid operator syntax and throws a 500
on `q=a & & b`. The search text is user input from a text field, so it must never be able
to fail the request.

### Relevance is `ts_rank`, and it becomes the default order for a search

The sort map gains `relevance` with the expression
`ts_rank(search_vector, websearch_to_tsquery('simple', :q))`. The default `ts_rank`
weights rank the `A` label above the `B` label, so a title hit sorts above a description
hit without any extra term. The controller picks the default: with `q`, the default is
`relevance,desc`; without `q`, the default stays `created,desc`, as today.

`relevance` needs the bound `:q` parameter, so the expression is valid only in a search.
A request that asks for `relevance` without `q` fails with
`InvalidTodoItemSortException`, which the existing handler turns into a 400
`ProblemDetail`. The guard lives in the controller, next to the check for an unknown sort
field, so the repository never builds SQL that cannot bind.

### A blank `q` means no search

The controller trims `q` and maps an empty result to `null`. An HTML form submits an
empty text field as `?q=`, and a client that clears the search box should see the full
list again, not an error and not an empty array. The 255-character limit comes from
`@Size` on the params record, so an over-long value fails as a validation error with a
`ProblemDetail`, like every other invalid parameter.

### The query record and the SQL fragment carry the search text

`TodoItemQuery` gains a `String searchText` component, and `TodoItemListParams` gains
`String q`. The fragment adds one condition and binds `:q` as a named parameter, so the
search text never reaches the SQL text. The sort expression for `relevance` uses the same
`:q` name, and `JdbcClient` binds one named parameter to both places.

## Risks / Trade-offs

- [A search text of punctuation only returns an empty array, which reads like a bug] → The
  spec states the behaviour, and an API test covers it. A client that wants to show "no
  results" already handles an empty array.
- [A search for a common word such as `the` matches almost every item] → `simple` holds no
  stop word list, so the word is a normal lexeme. The hit list is wide but correct, and
  the relevance order puts the closest match first.
- [`simple` does not stem, so `invoices` does not match `invoice`] → Stated as a
  non-goal. The `simple` configuration is one word in the migration and in the fragment;
  a later change can move to `english` or add `unaccent` with a new changeset that
  rebuilds the generated column.
- [The generated column grows the table and slows every write] → The column holds the
  lexemes of a title and a short description, and the GIN index is one more index on a
  table that already has three. A todo list is read far more than it is written.
- [Method validation on the bound params record may not fire, so an over-long `q` reaches
  the query] → Cover it with a controller test that asserts 400 for a 256-character `q`.
  If `@Valid` on the record does not produce a 400, check the length in the controller and
  throw a new exception that the handler maps to a 400, next to the sort exception.
- [`ts_rank` reads the `tsvector` of every matched row, so a very broad search sorts a
  large set] → Acceptable while the endpoint has no page limit; pagination is the fix, and
  it is out of scope for this change.

## Migration Plan

1. Liquibase applies `004-add-item-search.yaml` on startup. The statement adds the
   generated column, so PostgreSQL fills it for every existing row in one table rewrite.
   The `CREATE INDEX` then builds the GIN index.
2. No data migration and no backfill code. No application code reads the column before
   the changeset runs.
3. Rollback: drop the index and the column. No other change touches the schema, and no
   existing column changes, so a rollback loses only the search capability.

## Open Questions

None.
