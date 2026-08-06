# todo-items Specification

## Purpose
TBD - created by archiving change add-todo-rest-api. Update Purpose after archive.
## Requirements
### Requirement: Todo item structure

A todo item SHALL have an identifier, a title, a description, a done flag, a due
timestamp, a priority, a set of tags, a list of images, a created timestamp, and an
updated timestamp. The identifier SHALL be a UUID. The title SHALL NOT be blank and
SHALL be at most 255 characters. The description SHALL be optional. The due timestamp
SHALL be optional and SHALL carry a UTC offset. The priority SHALL be one of `LOW`,
`MEDIUM`, and `HIGH`, and SHALL be `MEDIUM` when a request omits it. A tag SHALL NOT be
blank and SHALL be at most 50 characters. An item SHALL hold at most 20 tags, and the
tags SHALL be unique within one item. An item SHALL hold at most 20 images. The system
SHALL set the identifier, the created timestamp, and the updated timestamp. A client
SHALL NOT set them.

Every todo item response SHALL hold an `images` array. Each entry SHALL hold the image
id, the content type, the size in bytes, the original file name, the created timestamp,
and the download path. The array SHALL be empty when the item holds no image. A client
SHALL NOT set the images through `/api/todos`. Only the endpoints under
`/api/todos/{id}/images` change the images of an item.

#### Scenario: Server assigns the identity and the timestamps

- **WHEN** the system creates a todo item
- **THEN** the item has a UUID identifier
- **AND** the created timestamp and the updated timestamp hold the time of the creation
- **AND** the image list of the item is empty

#### Scenario: Client cannot set the identity or the timestamps

- **WHEN** a request body contains an id, a created value, or an updated value
- **THEN** the system ignores those values

#### Scenario: Client cannot set the images through the item endpoints

- **WHEN** a request body to `POST /api/todos`, `PUT /api/todos/{id}`, or
  `PATCH /api/todos/{id}` contains an `images` value
- **THEN** the system ignores that value

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

### Requirement: Read one todo item

The API SHALL return one todo item from `GET /api/todos/{id}`.

#### Scenario: The item exists

- **WHEN** a client sends `GET /api/todos/{id}` for a stored item
- **THEN** the response status is 200
- **AND** the body holds that item

#### Scenario: The item does not exist

- **WHEN** a client sends `GET /api/todos/{id}` for an unknown id
- **THEN** the response status is 404
- **AND** the body is a `ProblemDetail`

### Requirement: Replace a todo item

The API SHALL replace a todo item through `PUT /api/todos/{id}`. The request SHALL
carry the title, the description, the done flag, the due timestamp, the priority, and
the tag list. The system SHALL overwrite all six fields and SHALL set the updated
timestamp to the time of the request. An omitted due timestamp SHALL clear the value,
an omitted tag list SHALL clear the tags, and an omitted priority SHALL set the
priority to `MEDIUM`. The identifier, the created timestamp, and the images SHALL stay
unchanged.

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

#### Scenario: Replace keeps the images

- **WHEN** a client sends `PUT /api/todos/{id}` for an item that holds two images
- **THEN** the item still holds both images
- **AND** the response body holds both images

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
tell a field that the request body omits from a field that the body holds as an explicit
JSON `null`. A field that the body omits SHALL keep its value. A field that the body
holds with a value SHALL take that value. An explicit `null` for the description or for
the due timestamp SHALL clear that field. An explicit `null` for the title, the done
flag, the priority, or the tag list SHALL return 400 with a `ProblemDetail` that names
the invalid field, and the stored item SHALL stay unchanged. A tag list in the body
SHALL replace the tags of the item. The system SHALL set the updated timestamp when it
changes the item.

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

#### Scenario: An explicit null clears the description

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"description": null}` for an
  item that has a description
- **THEN** the response status is 200
- **AND** the description of the item becomes null
- **AND** the title, the done flag, the due timestamp, the priority, and the tags are
  unchanged

#### Scenario: An explicit null clears the due timestamp

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"dueAt": null}` for an item
  that has a due timestamp
- **THEN** the response status is 200
- **AND** the due timestamp of the item becomes null
- **AND** the title, the description, the done flag, the priority, and the tags are
  unchanged

#### Scenario: An omitted description keeps its value

- **WHEN** a client sends `PATCH /api/todos/{id}` with a title only, for an item that
  has a description and a due timestamp
- **THEN** the description of the item is unchanged
- **AND** the due timestamp of the item is unchanged

#### Scenario: An empty body changes nothing

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{}`
- **THEN** the response status is 200
- **AND** every field of the item is unchanged

#### Scenario: An explicit null title is invalid

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"title": null}`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the stored item stays unchanged

#### Scenario: An explicit null done flag is invalid

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"done": null}`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the stored item stays unchanged

#### Scenario: An explicit null priority is invalid

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"priority": null}`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the stored item stays unchanged

#### Scenario: An explicit null tag list is invalid

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"tags": null}`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the stored item stays unchanged

#### Scenario: An explicit null in a tag list is invalid

- **WHEN** a client sends `PATCH /api/todos/{id}` with `{"tags": [null]}`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the stored item stays unchanged

#### Scenario: Patch an unknown item

- **WHEN** a client sends `PATCH /api/todos/{id}` for an unknown id
- **THEN** the response status is 404

#### Scenario: Patch with a blank title

- **WHEN** a client sends `PATCH /api/todos/{id}` with a blank title
- **THEN** the response status is 400
- **AND** the stored item stays unchanged

### Requirement: Delete a todo item

The API SHALL delete a todo item through `DELETE /api/todos/{id}`. The system SHALL
also remove the metadata and the stored objects of every image of that item.

#### Scenario: Delete an existing item

- **WHEN** a client sends `DELETE /api/todos/{id}` for a stored item
- **THEN** the response status is 204 with an empty body
- **AND** a later `GET /api/todos/{id}` returns 404

#### Scenario: Delete an item that holds images

- **WHEN** a client sends `DELETE /api/todos/{id}` for an item with two images
- **THEN** the response status is 204
- **AND** the database holds no metadata row for those images
- **AND** the object store holds no object for those images

#### Scenario: Delete an unknown item

- **WHEN** a client sends `DELETE /api/todos/{id}` for an unknown id
- **THEN** the response status is 404

### Requirement: Error responses

The API SHALL return an RFC 9457 `ProblemDetail` body for every error response. A
request for an item that does not exist SHALL return 404. A request that fails
validation SHALL return 400. A path variable that is not a UUID SHALL return 400.

#### Scenario: An identifier is not a UUID

- **WHEN** a client sends `GET /api/todos/not-a-uuid`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

#### Scenario: The error body has a media type

- **WHEN** the API returns a 400 response or a 404 response
- **THEN** the `Content-Type` header is `application/problem+json`

