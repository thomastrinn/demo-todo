## MODIFIED Requirements

### Requirement: Todo item structure

A todo item SHALL have an identifier, a title, a description, a done flag, a due
timestamp, a priority, a set of tags, a created timestamp, and an updated timestamp.
The identifier SHALL be a UUID. The title SHALL NOT be blank and SHALL be at most 255
characters. The description SHALL be optional. The due timestamp SHALL be optional and
SHALL carry a UTC offset. The priority SHALL be one of `LOW`, `MEDIUM`, and `HIGH`, and
SHALL be `MEDIUM` when a request omits it. A tag SHALL NOT be blank and SHALL be at
most 50 characters. An item SHALL hold at most 20 tags, and the tags SHALL be unique
within one item. The system SHALL set the identifier, the created timestamp, and the
updated timestamp. A client SHALL NOT set them.

#### Scenario: Server assigns the identity and the timestamps

- **WHEN** the system creates a todo item
- **THEN** the item has a UUID identifier
- **AND** the created timestamp and the updated timestamp hold the time of the creation

#### Scenario: Client cannot set the identity or the timestamps

- **WHEN** a request body contains an id, a created value, or an updated value
- **THEN** the system ignores those values

#### Scenario: An unknown priority is invalid

- **WHEN** a request body holds a priority that is not `LOW`, `MEDIUM`, or `HIGH`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`
- **AND** the stored data stays unchanged

#### Scenario: A tag list breaks a limit

- **WHEN** a request body holds a blank tag, a tag above 50 characters, or more than
  20 tags
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the stored data stays unchanged

#### Scenario: A duplicate tag collapses

- **WHEN** a request body holds the same tag two times
- **THEN** the item holds that tag one time

### Requirement: Create a todo item

The API SHALL create a todo item from `POST /api/todos`. The request MAY carry a due
timestamp, a priority, and a tag list. The response SHALL use status 201, SHALL carry a
`Location` header with the URI of the new item, and SHALL contain the created item.

#### Scenario: Create with a title and a description

- **WHEN** a client sends `POST /api/todos` with a title and a description
- **THEN** the response status is 201
- **AND** the `Location` header is `/api/todos/{id}` of the new item
- **AND** the body holds the title, the description, `done` as false, and the two
  timestamps

#### Scenario: Create with a title only

- **WHEN** a client sends `POST /api/todos` with a title and no description
- **THEN** the response status is 201
- **AND** the description of the new item is null
- **AND** the due timestamp of the new item is null
- **AND** the priority of the new item is `MEDIUM`
- **AND** the tag list of the new item is empty

#### Scenario: Create with a due timestamp, a priority, and tags

- **WHEN** a client sends `POST /api/todos` with a title, a due timestamp, a priority,
  and two tags
- **THEN** the response status is 201
- **AND** the body holds the due timestamp, the priority, and both tags

#### Scenario: Create with a blank title

- **WHEN** a client sends `POST /api/todos` with a missing title or a blank title
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the system creates no item

### Requirement: List the todo items

The API SHALL return the todo items from `GET /api/todos` with status 200. The endpoint
SHALL accept these optional filter parameters:

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
The field SHALL be one of `dueAt`, `priority`, `created`, `updated`, and `title`. The
direction SHALL be `asc` or `desc`, and SHALL be `asc` when the parameter omits it. A
sort over `priority` SHALL order the items by urgency, so `asc` puts `LOW` first and
`HIGH` last. A sort over `dueAt` SHALL put the items without a due timestamp last. The
default order SHALL be the created timestamp, newest first. The API SHALL break a tie
on the identifier, so that the order of two equal items stays stable.

#### Scenario: The store holds items

- **WHEN** a client sends `GET /api/todos`
- **THEN** the response status is 200
- **AND** the body is a JSON array with one entry for each stored item
- **AND** the newest item is first

#### Scenario: The store is empty

- **WHEN** a client sends `GET /api/todos` and no item exists
- **THEN** the response status is 200
- **AND** the body is an empty JSON array

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

#### Scenario: Sort by the due timestamp

- **WHEN** a client sends `GET /api/todos?sort=dueAt,asc`
- **THEN** the items with the earliest due timestamp come first
- **AND** the items without a due timestamp come last

#### Scenario: Sort by the priority

- **WHEN** a client sends `GET /api/todos?sort=priority,desc`
- **THEN** the items with the priority `HIGH` come first

#### Scenario: An unknown filter value

- **WHEN** a client sends `GET /api/todos?priority=URGENT`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

#### Scenario: An unknown sort field

- **WHEN** a client sends `GET /api/todos?sort=colour,asc`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

### Requirement: Replace a todo item

The API SHALL replace a todo item through `PUT /api/todos/{id}`. The request SHALL
carry the title, the description, the done flag, the due timestamp, the priority, and
the tag list. The system SHALL overwrite all six fields and SHALL set the updated
timestamp to the time of the request. An omitted due timestamp SHALL clear the value,
an omitted tag list SHALL clear the tags, and an omitted priority SHALL set the
priority to `MEDIUM`. The identifier and the created timestamp SHALL stay unchanged.

#### Scenario: Replace an existing item

- **WHEN** a client sends `PUT /api/todos/{id}` with a title, a description, and a
  done flag
- **THEN** the response status is 200
- **AND** the body holds the new values
- **AND** the created timestamp is unchanged
- **AND** the updated timestamp is later than or equal to the created timestamp

#### Scenario: Replace clears an omitted description

- **WHEN** a client sends `PUT /api/todos/{id}` without a description for an item that
  has one
- **THEN** the description of the item becomes null

#### Scenario: Replace clears the due timestamp and the tags

- **WHEN** a client sends `PUT /api/todos/{id}` without a due timestamp, a priority,
  and a tag list, for an item that holds all three
- **THEN** the due timestamp of the item becomes null
- **AND** the tag list of the item becomes empty
- **AND** the priority of the item becomes `MEDIUM`

#### Scenario: Replace sets a new tag list

- **WHEN** a client sends `PUT /api/todos/{id}` with a tag list for an item that holds
  other tags
- **THEN** the item holds only the tags of the request

#### Scenario: Replace an unknown item

- **WHEN** a client sends `PUT /api/todos/{id}` for an unknown id
- **THEN** the response status is 404

#### Scenario: Replace with a blank title

- **WHEN** a client sends `PUT /api/todos/{id}` with a blank title
- **THEN** the response status is 400
- **AND** the stored item stays unchanged

### Requirement: Change a todo item in part

The API SHALL apply a partial update through `PATCH /api/todos/{id}`. The system SHALL
change only the fields that the request body contains. A field that the body omits
SHALL keep its value. A tag list in the body SHALL replace the tags of the item. The
system SHALL set the updated timestamp when it changes the item.

#### Scenario: Set the done flag only

- **WHEN** a client sends `PATCH /api/todos/{id}` with `done` as true
- **THEN** the response status is 200
- **AND** the item is done
- **AND** the title and the description are unchanged

#### Scenario: Change the title only

- **WHEN** a client sends `PATCH /api/todos/{id}` with a title
- **THEN** the item has the new title
- **AND** the description and the done flag are unchanged

#### Scenario: Change the due timestamp only

- **WHEN** a client sends `PATCH /api/todos/{id}` with a due timestamp
- **THEN** the item has the new due timestamp
- **AND** the priority and the tags are unchanged

#### Scenario: Change the priority only

- **WHEN** a client sends `PATCH /api/todos/{id}` with a priority
- **THEN** the item has the new priority
- **AND** the due timestamp and the tags are unchanged

#### Scenario: Replace the tags only

- **WHEN** a client sends `PATCH /api/todos/{id}` with a tag list
- **THEN** the item holds only the tags of the request
- **AND** the due timestamp and the priority are unchanged

#### Scenario: An empty tag list clears the tags

- **WHEN** a client sends `PATCH /api/todos/{id}` with an empty tag list
- **THEN** the item holds no tag

#### Scenario: Patch an unknown item

- **WHEN** a client sends `PATCH /api/todos/{id}` for an unknown id
- **THEN** the response status is 404

#### Scenario: Patch with a blank title

- **WHEN** a client sends `PATCH /api/todos/{id}` with a blank title
- **THEN** the response status is 400
- **AND** the stored item stays unchanged
