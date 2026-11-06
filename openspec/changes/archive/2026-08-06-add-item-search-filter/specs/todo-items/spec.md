## ADDED Requirements

### Requirement: Full-text search over the title and the description

The API SHALL match the value of the `q` parameter against the title and the description
of a todo item. The match SHALL run over whole words, and it SHALL ignore the case of
both the search text and the item text. The match SHALL NOT depend on the position of
the word inside the field.

The search text SHALL support these forms:

- Two or more words: the API returns only the items that hold every one of the words.
- A quoted phrase: the API returns only the items that hold those words next to each
  other, in that order.
- The word `or` between two words: the API returns the items that hold either word.
- A leading `-` on a word: the API returns only the items that do not hold that word.

The API SHALL rank a match in the title above a match in the description. The search
SHALL NOT match the tags, the identifier, or any other field. `q` SHALL be at most 255
characters.

#### Scenario: A word matches the title

- **WHEN** a client sends `GET /api/todos?q=invoice`
- **THEN** the body holds the items whose title holds the word `invoice`

#### Scenario: A word matches the description

- **WHEN** a client sends `GET /api/todos?q=invoice`
- **THEN** the body holds the items whose description holds the word `invoice`
- **AND** the body holds no item that holds the word in neither field

#### Scenario: The match ignores the case

- **WHEN** a client sends `GET /api/todos?q=INVOICE` for an item with the title
  `Pay the invoice`
- **THEN** the body holds that item

#### Scenario: The match reads a word inside the text

- **WHEN** a client sends `GET /api/todos?q=invoice` for an item with the title
  `Pay the invoice today`
- **THEN** the body holds that item

#### Scenario: Two words need both

- **WHEN** a client sends `GET /api/todos?q=invoice%20office`
- **THEN** the body holds only the items that hold both words
- **AND** the body holds no item that holds one word only

#### Scenario: A quoted phrase needs the word order

- **WHEN** a client sends `GET /api/todos?q="pay%20the%20invoice"`
- **THEN** the body holds only the items that hold those three words next to each other
- **AND** the body holds no item that holds the same words in another order

#### Scenario: An `or` widens the search

- **WHEN** a client sends `GET /api/todos?q=invoice%20or%20receipt`
- **THEN** the body holds the items that hold `invoice` and the items that hold
  `receipt`

#### Scenario: A leading minus excludes a word

- **WHEN** a client sends `GET /api/todos?q=invoice%20-office`
- **THEN** the body holds the items that hold `invoice` and not `office`

#### Scenario: The search text holds no searchable word

- **WHEN** a client sends `GET /api/todos?q=%2B%2B%2B`
- **THEN** the response status is 200
- **AND** the body is an empty JSON array

#### Scenario: The search text is too long

- **WHEN** a client sends `GET /api/todos?q=` with more than 255 characters
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

#### Scenario: The search does not read the tags

- **WHEN** a client sends `GET /api/todos?q=work` for an item that holds the tag `work`
  and holds the word in neither the title nor the description
- **THEN** the body holds no item

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

#### Scenario: The store holds items

- **WHEN** a client sends `GET /api/todos`
- **THEN** the response status is 200
- **AND** the body is a JSON array with one entry for each stored item
- **AND** the newest item is first

#### Scenario: The store is empty

- **WHEN** a client sends `GET /api/todos` and no item exists
- **THEN** the response status is 200
- **AND** the body is an empty JSON array

#### Scenario: Search by text

- **WHEN** a client sends `GET /api/todos?q=invoice`
- **THEN** the response status is 200
- **AND** the body holds only the items that match the search text
- **AND** the best match is first

#### Scenario: A blank search text returns every item

- **WHEN** a client sends `GET /api/todos?q=%20`
- **THEN** the body holds one entry for each stored item
- **AND** the newest item is first

#### Scenario: Filter by priority

- **WHEN** a client sends `GET /api/todos?priority=HIGH`
- **THEN** the body holds only the items with the priority `HIGH`

#### Scenario: Filter by two priorities

- **WHEN** a client sends `GET /api/todos?priority=HIGH&priority=LOW`
- **THEN** the body holds the items with the priority `HIGH` and the items with the
  priority `LOW`

#### Scenario: Filter by tag

- **WHEN** a client sends `GET /api/todos?tag=work`
- **THEN** the body holds only the items that hold the tag `work`

#### Scenario: Filter by two tags

- **WHEN** a client sends `GET /api/todos?tag=work&tag=urgent`
- **THEN** the body holds only the items that hold both tags

#### Scenario: Filter by a due window

- **WHEN** a client sends `GET /api/todos?dueAfter=2026-01-01T00:00:00Z&dueBefore=2026-02-01T00:00:00Z`
- **THEN** the body holds only the items with a due timestamp inside that window
- **AND** the body holds no item without a due timestamp

#### Scenario: Combine a filter and the done flag

- **WHEN** a client sends `GET /api/todos?tag=work&done=false`
- **THEN** the body holds only the open items that hold the tag `work`

#### Scenario: Combine the search text and the priority

- **WHEN** a client sends `GET /api/todos?q=invoice&priority=HIGH`
- **THEN** the body holds only the items that match the search text and hold the
  priority `HIGH`

#### Scenario: Combine the search text and the done flag

- **WHEN** a client sends `GET /api/todos?q=invoice&done=false`
- **THEN** the body holds only the open items that match the search text

#### Scenario: Combine the search text with a tag and a due window

- **WHEN** a client sends
  `GET /api/todos?q=invoice&tag=work&dueBefore=2026-02-01T00:00:00Z`
- **THEN** the body holds only the items that match the search text, hold the tag
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
- **THEN** the body holds only the items that match the search text
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
