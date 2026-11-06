## ADDED Requirements

### Requirement: Image structure

An image of a todo item SHALL have an identifier, the identifier of the todo item, a
content type, a size in bytes, an original file name, a storage key, and a created
timestamp. The identifier SHALL be a UUID. The storage key SHALL be unique over all
images. The system SHALL set the identifier, the storage key, the size, and the created
timestamp. A client SHALL NOT set them.

#### Scenario: Server assigns the identity and the metadata

- **WHEN** the system stores an image
- **THEN** the image has a UUID identifier
- **AND** the image has a unique storage key
- **AND** the size holds the byte count of the uploaded file
- **AND** the created timestamp holds the time of the upload

#### Scenario: The original file name is optional

- **WHEN** a client uploads a file part without a file name
- **THEN** the system stores the image with a null file name

### Requirement: Upload an image

The API SHALL add an image to a todo item through `POST /api/todos/{id}/images`. The
request SHALL use `multipart/form-data` and SHALL carry the bytes in a part named
`file`. The response SHALL use status 201, SHALL carry a `Location` header with the URI
of the new image, and SHALL contain the metadata of the new image.

The API SHALL accept only a content type that starts with `image/`. The API SHALL
reject a file above 10 MB. A todo item SHALL hold at most 20 images.

#### Scenario: Upload the first image

- **WHEN** a client sends `POST /api/todos/{id}/images` with a PNG file
- **THEN** the response status is 201
- **AND** the `Location` header is `/api/todos/{id}/images/{imageId}` of the new image
- **AND** the body holds the image id, the content type `image/png`, the size, the file
  name, and the created timestamp

#### Scenario: Upload a second image

- **WHEN** a client uploads two files to the same todo item
- **THEN** both uploads answer 201
- **AND** the two images have different identifiers
- **AND** a later `GET /api/todos/{id}/images` holds both images

#### Scenario: Upload to an unknown item

- **WHEN** a client sends `POST /api/todos/{id}/images` for an unknown id
- **THEN** the response status is 404
- **AND** the body is a `ProblemDetail`
- **AND** the system stores no object

#### Scenario: Upload a file that is not an image

- **WHEN** a client uploads a part with the content type `application/pdf`
- **THEN** the response status is 415
- **AND** the body is a `ProblemDetail`
- **AND** the system stores no image

#### Scenario: Upload a file above the size limit

- **WHEN** a client uploads a file above 10 MB
- **THEN** the response status is 413
- **AND** the body is a `ProblemDetail`
- **AND** the system stores no image

#### Scenario: Upload past the image limit

- **WHEN** a client uploads an image to a todo item that already holds 20 images
- **THEN** the response status is 409
- **AND** the body is a `ProblemDetail`
- **AND** the item still holds 20 images

#### Scenario: Upload without the file part

- **WHEN** a client sends `POST /api/todos/{id}/images` without a part named `file`
- **THEN** the response status is 400
- **AND** the body is a `ProblemDetail`

### Requirement: List the images of a todo item

The API SHALL return the image metadata of a todo item from
`GET /api/todos/{id}/images` with status 200. The API SHALL order the images by the
created timestamp, oldest first, and SHALL break a tie on the identifier. The response
SHALL NOT hold the image bytes.

#### Scenario: The item holds images

- **WHEN** a client sends `GET /api/todos/{id}/images` for an item with two images
- **THEN** the response status is 200
- **AND** the body is a JSON array with one entry for each image
- **AND** the oldest image is first

#### Scenario: The item holds no image

- **WHEN** a client sends `GET /api/todos/{id}/images` for an item without an image
- **THEN** the response status is 200
- **AND** the body is an empty JSON array

#### Scenario: List the images of an unknown item

- **WHEN** a client sends `GET /api/todos/{id}/images` for an unknown id
- **THEN** the response status is 404
- **AND** the body is a `ProblemDetail`

### Requirement: Download an image

The API SHALL answer `GET /api/todos/{id}/images/{imageId}` with status 302 and a
`Location` header. The `Location` header SHALL hold a presigned URL of the stored
object in the object store. The API SHALL NOT carry the image bytes. The presigned URL
SHALL expire after a configured lifetime, and the default lifetime SHALL be 5 minutes.
The URL SHALL make the object store answer with the stored content type and with an
inline `Content-Disposition` that carries the original file name when the image has
one.

The endpoint path SHALL stay valid after the URL expires. Every call SHALL build a new
URL.

#### Scenario: Download a stored image

- **WHEN** a client sends `GET /api/todos/{id}/images/{imageId}` for a stored image
- **THEN** the response status is 302
- **AND** the `Location` header holds a presigned URL of the object store
- **AND** a request to that URL answers 200 with the same bytes as the upload
- **AND** the `Content-Type` of that answer holds the content type of the upload

#### Scenario: A later call builds a new URL

- **WHEN** a client sends `GET /api/todos/{id}/images/{imageId}` two times
- **THEN** both responses hold status 302
- **AND** both `Location` headers work

#### Scenario: The presigned URL expires

- **WHEN** a client opens a presigned URL after the lifetime passes
- **THEN** the object store refuses the request
- **AND** a new `GET /api/todos/{id}/images/{imageId}` gives a URL that works

#### Scenario: Download an unknown image

- **WHEN** a client sends `GET /api/todos/{id}/images/{imageId}` for an unknown image id
- **THEN** the response status is 404
- **AND** the body is a `ProblemDetail`
- **AND** the API sends no request to the object store

#### Scenario: Download an image of another item

- **WHEN** a client sends `GET /api/todos/{id}/images/{imageId}` with an image that
  belongs to a different todo item
- **THEN** the response status is 404
- **AND** the body is a `ProblemDetail`

### Requirement: Delete an image

The API SHALL remove one image through `DELETE /api/todos/{id}/images/{imageId}`. The
system SHALL remove the metadata and the stored object. The todo item SHALL stay.

#### Scenario: Delete a stored image

- **WHEN** a client sends `DELETE /api/todos/{id}/images/{imageId}` for a stored image
- **THEN** the response status is 204 with an empty body
- **AND** a later `GET /api/todos/{id}/images/{imageId}` returns 404
- **AND** the todo item still exists

#### Scenario: Delete an unknown image

- **WHEN** a client sends `DELETE /api/todos/{id}/images/{imageId}` for an unknown
  image id
- **THEN** the response status is 404
- **AND** the body is a `ProblemDetail`

#### Scenario: Delete an image of another item

- **WHEN** a client sends `DELETE /api/todos/{id}/images/{imageId}` with an image that
  belongs to a different todo item
- **THEN** the response status is 404
- **AND** the image stays

### Requirement: Store the image bytes in an object store

The system SHALL hold the image bytes in an S3-compatible object store and SHALL hold
only the metadata in the database. The bytes of an upload SHALL pass through the
system. The bytes of a download SHALL NOT pass through the system: a client SHALL read
them from the object store directly. The system SHALL write the object before the
metadata row. When the metadata write fails, the system SHALL remove the object. The
system SHALL remove the metadata row before the object.

#### Scenario: An upload writes the object and the row

- **WHEN** the system accepts an upload
- **THEN** the object store holds an object under the storage key of the image
- **AND** the database holds one metadata row for the image

#### Scenario: The metadata write fails

- **WHEN** the object store holds the new object and the metadata write fails
- **THEN** the system removes the object
- **AND** the object store holds no object under that storage key

#### Scenario: The object store is unreachable

- **WHEN** a client uploads an image and the object store does not answer
- **THEN** the response status is 503
- **AND** the body is a `ProblemDetail`
- **AND** the database holds no metadata row for that upload
