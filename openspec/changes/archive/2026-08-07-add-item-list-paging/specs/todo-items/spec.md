## MODIFIED Requirements

### Requirement: List the todo items

The API SHALL return the todo items from `GET /api/todos` with status 200. The endpoint
SHALL accept these optional filter parameters:

- `q`: the API returns the items whose title or description matches the search text. A
  blank value or a value of only spaces SHALL behave as if the request omits the
  parameter.
- `priority`: the API returns the items with that priority. A client MAY repeat the
  parameter, and the API then returns the items that match any of the values.
- `tag`: the API returns the items that hold that tag. A client MAY repeat the
  parameter, and the API then returns the items that hold every one of the tags.
- `done`: the API returns the items with that done flag.
- `dueBefore`: the API returns the items with a due timestamp before that value.
- `dueAfter`: the API returns the items with a due timestamp after that value.

An item without a due timestamp SHALL NOT match `dueBefore` or `dueAfter`. When a
request holds more than one filter parameter, the API SHALL return only the items that
match all of them.

The endpoint SHALL accept an optional `sort` parameter in the form `<field>,<direction>`.
The field SHALL be one of `relevance`, `dueAt`, `priority`, `created`, `updated`, and
`title`. The direction SHALL be `asc` or `desc`, and SHALL be `asc` when the parameter
omits it. A sort over `priority` SHALL order the items by urgency, so `asc` puts `LOW`
first and `HIGH` last. A sort over `dueAt` SHALL put the items without a due timestamp
last. A sort over `relevance` SHALL order the items by the strength of the match, so
`desc` puts the best match first, and the API SHALL reject the value when the request
holds no `q`. The API SHALL break a tie on the identifier, so that the order of two equal
items stays stable.

The default order SHALL depend on the request. When the request holds a `q` value, the
default order SHALL be the relevance, best match first. In every other case the default
order SHALL be the created timestamp, newest first.

The endpoint SHALL return one page of the matches. It SHALL accept two optional paging
parameters:

- `limit`: the greatest number of items in the page. The value SHALL be an integer from
  1 to 100. The default SHALL be 20.
- `offset`: the number of matches that the API skips before the first item of the page.
  The value SHALL be an integer of 0 or more. The default SHALL be 0.

The API SHALL apply the paging after the filters and after the sort. The API SHALL
reject a `limit` or an `offset` that is not an integer, or that is outside its range,
with status 400 and a `ProblemDetail` that names the invalid parameter. An `offset` past
the last match SHALL return an empty page with status 200.

The response body SHALL be a JSON object with these fields:

- `items`: an array with one entry for each item of the page, in the order of the sort.
  The entry SHALL hold the same fields as the response of a read of one item.
- `limit`: the limit that the API applied.
- `offset`: the offset that the API applied.
- `total`: the number of matches for the filters, over every page, not the number of
  items in this page.

#### Scenario: The store holds items

- **WHEN** a client sends `GET /api/todos`
- **THEN** the response status is 200
- **AND** the body is a JSON object
- **AND** `items` holds one entry for each stored item, up to the default limit
- **AND** the newest item is first
- **AND** `limit` is 20, `offset` is 0, and `total` is the number of stored items

#### Scenario: The store is empty

- **WHEN** a client sends `GET /api/todos` and no item exists
- **THEN** the response status is 200
- **AND** `items` is an empty array
- **AND** `total` is 0

#### Scenario: A page holds the first items

- **WHEN** a client sends `GET /api/todos?limit=2` and the store holds five items
- **THEN** `items` holds the first two items of the order
- **AND** `limit` is 2, `offset` is 0, and `total` is 5

#### Scenario: An offset skips the earlier matches

- **WHEN** a client sends `GET /api/todos?limit=2&offset=2` and the store holds five
  items
- **THEN** `items` holds the third and the fourth item of the order
- **AND** `limit` is 2, `offset` is 2, and `total` is 5

#### Scenario: The last page holds the rest

- **WHEN** a client sends `GET /api/todos?limit=2&offset=4` and the store holds five
  items
- **THEN** `items` holds one entry
- **AND** `total` is 5

#### Scenario: An offset past the last match

- **WHEN** a client sends `GET /api/todos?offset=500` and the store holds five items
- **THEN** the response status is 200
- **AND** `items` is an empty array
- **AND** `total` is 5

#### Scenario: The total counts the matches of the filter

- **WHEN** a client sends `GET /api/todos?done=false&limit=1`
- **THEN** `items` holds one entry
- **AND** `total` is the number of open items, not the number of stored items

#### Scenario: The paging keeps the sort

- **WHEN** a client sends `GET /api/todos?sort=title,asc&limit=2&offset=2`
- **THEN** `items` holds the third and the fourth item of the title order

#### Scenario: A limit below the range

- **WHEN** a client sends `GET /api/todos?limit=0`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names `limit`

#### Scenario: A limit above the range

- **WHEN** a client sends `GET /api/todos?limit=101`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names `limit`

#### Scenario: A negative offset

- **WHEN** a client sends `GET /api/todos?offset=-1`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names `offset`

#### Scenario: A limit that is not a number

- **WHEN** a client sends `GET /api/todos?limit=all`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` with the detail `limit must be a number`

#### Scenario: Search by text

- **WHEN** a client sends `GET /api/todos?q=invoice`
- **THEN** the response status is 200
- **AND** `items` holds only the items that match the search text
- **AND** the best match is first

#### Scenario: A blank search text returns every item

- **WHEN** a client sends `GET /api/todos?q=%20`
- **THEN** `items` holds one entry for each stored item, up to the default limit
- **AND** the newest item is first

#### Scenario: Filter by priority

- **WHEN** a client sends `GET /api/todos?priority=HIGH`
- **THEN** `items` holds only the items with the priority `HIGH`

#### Scenario: Filter by two priorities

- **WHEN** a client sends `GET /api/todos?priority=HIGH&priority=LOW`
- **THEN** `items` holds the items with the priority `HIGH` and the items with the
  priority `LOW`

#### Scenario: Filter by tag

- **WHEN** a client sends `GET /api/todos?tag=work`
- **THEN** `items` holds only the items that hold the tag `work`

#### Scenario: Filter by two tags

- **WHEN** a client sends `GET /api/todos?tag=work&tag=urgent`
- **THEN** `items` holds only the items that hold both tags

#### Scenario: Filter by a due window

- **WHEN** a client sends `GET /api/todos?dueAfter=2026-01-01T00:00:00Z&dueBefore=2026-02-01T00:00:00Z`
- **THEN** `items` holds only the items with a due timestamp inside that window
- **AND** `items` holds no item without a due timestamp

#### Scenario: Combine a filter and the done flag

- **WHEN** a client sends `GET /api/todos?tag=work&done=false`
- **THEN** `items` holds only the open items that hold the tag `work`

#### Scenario: Combine the search text and the priority

- **WHEN** a client sends `GET /api/todos?q=invoice&priority=HIGH`
- **THEN** `items` holds only the items that match the search text and hold the
  priority `HIGH`

#### Scenario: Combine the search text and the done flag

- **WHEN** a client sends `GET /api/todos?q=invoice&done=false`
- **THEN** `items` holds only the open items that match the search text

#### Scenario: Combine the search text with a tag and a due window

- **WHEN** a client sends
  `GET /api/todos?q=invoice&tag=work&dueBefore=2026-02-01T00:00:00Z`
- **THEN** `items` holds only the items that match the search text, hold the tag
  `work`, and have a due timestamp before that value

#### Scenario: Sort by the due timestamp

- **WHEN** a client sends `GET /api/todos?sort=dueAt,asc`
- **THEN** the items with the earliest due timestamp come first
- **AND** the items without a due timestamp come last

#### Scenario: Sort by the priority

- **WHEN** a client sends `GET /api/todos?sort=priority,desc`
- **THEN** the items with the priority `HIGH` come first

#### Scenario: A search with an explicit sort

- **WHEN** a client sends `GET /api/todos?q=invoice&sort=dueAt,asc`
- **THEN** `items` holds only the items that match the search text
- **AND** the items with the earliest due timestamp come first

#### Scenario: Sort by the relevance

- **WHEN** a client sends `GET /api/todos?q=invoice&sort=relevance,desc`
- **THEN** the item with the strongest match is first

#### Scenario: A title match ranks above a description match

- **WHEN** a client sends `GET /api/todos?q=invoice` for two items, where one holds the
  word in the title and the other holds it in the description
- **THEN** the item with the word in the title is first

#### Scenario: A relevance sort without a search text

- **WHEN** a client sends `GET /api/todos?sort=relevance,desc` and the request holds no
  `q`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

#### Scenario: An unknown filter value

- **WHEN** a client sends `GET /api/todos?priority=URGENT`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

#### Scenario: An unknown sort field

- **WHEN** a client sends `GET /api/todos?sort=colour,asc`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`
