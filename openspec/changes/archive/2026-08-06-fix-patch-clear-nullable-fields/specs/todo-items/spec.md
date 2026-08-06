## MODIFIED Requirements

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
