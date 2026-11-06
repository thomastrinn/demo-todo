## Why

A todo item carries only text today. Many tasks are easier to understand with a
picture: a photo of a broken part, a screenshot of an error, a scan of a receipt.
Clients have no place to put that picture, so they keep it outside the system and
lose the link to the item.

## What Changes

- A todo item gains a collection of images. An item holds at most 20 images.
- New sub-resource endpoints under `/api/todos/{id}/images`:
  - `POST` uploads one image as `multipart/form-data` and answers 201 with the
    image metadata and a `Location` header.
  - `GET` lists the image metadata of the item.
  - `GET /{imageId}` answers 302 with a short-lived presigned URL of the object
    store. The client reads the bytes from the store directly.
  - `DELETE /{imageId}` removes one image.
- The API accepts any content type that starts with `image/`. The API rejects a
  file above 10 MB with status 413.
- The todo item response gains an `images` array. Each entry holds the image id,
  the content type, the size, the original file name, the created timestamp, and
  the download path.
- `DELETE /api/todos/{id}` also removes the images of that item, in the database
  and in the object store.
- The application stores the image bytes in an S3-compatible object store
  (Garage). The application stores only the metadata in PostgreSQL.
- New infrastructure: a `garage` service in `compose.yaml` and a Garage
  container for the tests.
- New dependency: the AWS SDK for Java v2 S3 client.

No endpoint changes shape in a breaking way. An existing client that ignores the
new `images` field keeps working.

## Capabilities

### New Capabilities

- `todo-item-images`: upload, list, download, and delete the images of a todo
  item; the limits on an upload; the object store that holds the bytes.

### Modified Capabilities

- `todo-items`: the item structure gains an image collection, the item response
  gains an `images` array, and the delete of an item also removes its images.

## Impact

- Code: `hu.wbstr.todo.item` gains the image domain type, the repository, the
  object store client, the service, the controller endpoints, and the response
  types.
- API: four new endpoints; a new `images` field in every todo item response.
- Database: a new `todo_item_image` table, added by a new Liquibase changeset.
- Dependencies: `software.amazon.awssdk:s3` with the AWS SDK BOM.
- Configuration: new properties for the object store endpoint, the public
  endpoint that the client uses, the region, the bucket, the credentials, and the
  presign lifetime; a multipart size limit.
- Infrastructure: a `garage` service in `compose.yaml`; a Garage container in
  the test configuration.
- Tests: new repository, controller, and API tests; the existing item tests must
  accept the new response field.
