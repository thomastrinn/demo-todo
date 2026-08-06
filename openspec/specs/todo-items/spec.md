# todo-items Specification

## Purpose
TBD - created by archiving change add-todo-rest-api. Update Purpose after archive.
## Requirements
### Requirement: Todo item structure

A todo item SHALL have an identifier, a title, a description, a done flag, a created
timestamp, and an updated timestamp. The identifier SHALL be a UUID. The title SHALL
NOT be blank and SHALL be at most 255 characters. The description SHALL be optional.
The system SHALL set the identifier, the created timestamp, and the updated timestamp.
A client SHALL NOT set them.

#### Scenario: Server assigns the identity and the timestamps

- **WHEN** the system creates a todo item
- **THEN** the item has a UUID identifier
- **AND** the created timestamp and the updated timestamp hold the time of the creation

#### Scenario: Client cannot set the identity or the timestamps

- **WHEN** a request body contains an id, a created value, or an updated value
- **THEN** the system ignores those values

### Requirement: Create a todo item

The API SHALL create a todo item from `POST /api/todos`. The response SHALL use status
201, SHALL carry a `Location` header with the URI of the new item, and SHALL contain
the created item.

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

#### Scenario: Create with a blank title

- **WHEN** a client sends `POST /api/todos` with a missing title or a blank title
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail` that names the invalid field
- **AND** the system creates no item

### Requirement: List the todo items

The API SHALL return all todo items from `GET /api/todos` with status 200.

#### Scenario: The store holds items

- **WHEN** a client sends `GET /api/todos`
- **THEN** the response status is 200
- **AND** the body is a JSON array with one entry for each stored item

#### Scenario: The store is empty

- **WHEN** a client sends `GET /api/todos` and no item exists
- **THEN** the response status is 200
- **AND** the body is an empty JSON array

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
carry the title, the description, and the done flag. The system SHALL overwrite all
three fields and SHALL set the updated timestamp to the time of the request. The
identifier and the created timestamp SHALL stay unchanged.

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
SHALL keep its value. The system SHALL set the updated timestamp when it changes the
item.

#### Scenario: Set the done flag only

- **WHEN** a client sends `PATCH /api/todos/{id}` with `done` as true
- **THEN** the response status is 200
- **AND** the item is done
- **AND** the title and the description are unchanged

#### Scenario: Change the title only

- **WHEN** a client sends `PATCH /api/todos/{id}` with a title
- **THEN** the item has the new title
- **AND** the description and the done flag are unchanged

#### Scenario: Patch an unknown item

- **WHEN** a client sends `PATCH /api/todos/{id}` for an unknown id
- **THEN** the response status is 404

#### Scenario: Patch with a blank title

- **WHEN** a client sends `PATCH /api/todos/{id}` with a blank title
- **THEN** the response status is 400
- **AND** the stored item stays unchanged

### Requirement: Delete a todo item

The API SHALL delete a todo item through `DELETE /api/todos/{id}`.

#### Scenario: Delete an existing item

- **WHEN** a client sends `DELETE /api/todos/{id}` for a stored item
- **THEN** the response status is 204 with an empty body
- **AND** a later `GET /api/todos/{id}` returns 404

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

