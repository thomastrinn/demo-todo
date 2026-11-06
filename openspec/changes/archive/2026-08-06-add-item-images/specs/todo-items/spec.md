## MODIFIED Requirements

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
