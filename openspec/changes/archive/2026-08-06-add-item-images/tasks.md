## 1. Object store infrastructure

- [x] 1.1 Start `dxflrs/garage:v2.3.0` by hand and check that
      `server --single-node --default-bucket` creates the layout, the key, and the
      bucket. If the flags are absent, use the fallback from design.md and record the
      change in design.md.
- [x] 1.2 Add `garage/garage.toml` with `metadata_dir`, `data_dir`,
      `db_engine = "sqlite"`, `replication_factor = 1`, `rpc_bind_addr`, a fixed
      `rpc_secret`, and an `[s3_api]` block that binds 3900 and sets
      `s3_region = "garage"`.
- [x] 1.3 Add the `garage` service to `compose.yaml`: the image, the command, the three
      `GARAGE_DEFAULT_*` environment variables, the mount of `garage/garage.toml`, and
      port 3900.
- [x] 1.4 Add the `software.amazon.awssdk:bom` import to `dependencyManagement` and the
      `software.amazon.awssdk:s3` dependency to `pom.xml`.
- [x] 1.5 Run `./mvnw -q dependency:resolve` and check that the build resolves the SDK.

## 2. Configuration and the S3 client

- [x] 2.1 Add the record `TodoItemImageProperties` with
      `@ConfigurationProperties("todo.images")` for the endpoint, the public endpoint
      (default: the endpoint), the region, the bucket, the access key, the secret key,
      and the presign lifetime (default: 5 minutes).
- [x] 2.2 Add the `S3Client` bean to `TodoItemConfiguration` with `endpointOverride`,
      `forcePathStyle(true)`, the static credentials, and the region.
- [x] 2.3 Add the `S3Presigner` bean with the same credentials and region, and with
      `endpointOverride` set to the public endpoint.
- [x] 2.4 Add the `todo.images.*` defaults and the multipart limits
      (`spring.servlet.multipart.max-file-size=10MB`,
      `spring.servlet.multipart.max-request-size=11MB`) to `application.properties`.
- [x] 2.5 Add `TodoItemImageStore` with `put(key, contentType, size, stream)`,
      `presignGet(key, contentType, fileName)`, and `delete(key)`. Wrap `SdkException`
      in `ImageStoreUnavailableException`. The presign request sets
      `responseContentType` and an inline `responseContentDisposition`.

## 3. Schema and persistence

- [x] 3.1 Add the Liquibase changeset `003-add-item-images.yaml` that creates
      `todo_item_image` with `id`, `todo_item_id`, `content_type`, `size_bytes`,
      `file_name`, `storage_key`, and `created`, a foreign key to `todo_item` with
      `ON DELETE CASCADE`, a unique constraint on `storage_key`, and an index on
      `todo_item_id`.
- [x] 3.2 Register the changeset in `db.changelog-master.yaml`.
- [x] 3.3 Add the record `TodoItemImage` as an aggregate root with `@Id UUID id` and
      the `@Transient boolean isNew` flag, in the same style as `TodoItem`.
- [x] 3.4 Add `TodoItemImageRepository` with `findByTodoItemIdOrderByCreatedAscIdAsc`,
      `findByTodoItemIdInOrderByCreatedAscIdAsc`, `findByIdAndTodoItemId`, and
      `countByTodoItemId`.

## 4. Test infrastructure

- [x] 4.1 Copy `garage.toml` to `src/test/resources` or point the test container at the
      repository file.
- [x] 4.2 Add the Garage `GenericContainer` bean to `TestcontainersConfiguration`: the
      image, the command, the environment variables, the copied config, the exposed
      port 3900, and a wait strategy on that port.
- [x] 4.3 Add a `DynamicPropertyRegistrar` bean that binds `todo.images.endpoint`,
      `todo.images.bucket`, `todo.images.access-key`, and `todo.images.secret-key` to
      the container.
- [x] 4.4 Add `TodoItemImageRepositoryTest` with `@DataJdbcTest`: insert, read by item,
      read by id and item, count, and the cascade delete of the parent item.

## 5. Service

- [x] 5.1 Add `TodoItemImageService.store(itemId, file)`: check the item exists, check
      the content type starts with `image/`, check the count against the limit of 20,
      write the object, then insert the row, and delete the object when the insert
      fails.
- [x] 5.2 Add `findAll(itemId)`, `findOne(itemId, imageId)`, and
      `downloadUrl(itemId, imageId)`, each returning 404 through
      `TodoItemImageNotFoundException` when the image does not belong to that item.
      `downloadUrl` reads the metadata row first and calls the store only after that.
- [x] 5.3 Add `delete(itemId, imageId)`: delete the row first, then the object.
- [x] 5.4 Change `TodoItemService.delete` to read the storage keys, delete the item,
      then delete the objects.
- [x] 5.5 Add `TodoItemService` support for the `images` field: read the images of one
      item for `findById`, and read them for the whole list with one batch query in
      `findAll`.
- [x] 5.6 Add the exceptions `TodoItemImageNotFoundException`,
      `UnsupportedImageTypeException`, `TooManyImagesException`, and
      `ImageStoreUnavailableException`.

## 6. HTTP layer

- [x] 6.1 Add `TodoItemImageResponse` with the id, the content type, the size, the file
      name, the created timestamp, and the download path.
- [x] 6.2 Add the `images` field to `TodoItemResponse` and to `TodoItemResponse.of`.
- [x] 6.3 Add `TodoItemImageController` under `/api/todos/{id}/images` with the
      `POST` (multipart part `file`, 201 and `Location`), the `GET` list, the
      `GET /{imageId}` download, and the `DELETE /{imageId}` (204).
- [x] 6.4 Answer the download with status 302 and the presigned URL in the `Location`
      header. Send no body.
- [x] 6.5 Add `TodoItemImageExceptionHandler` that maps the six conditions from
      design.md to 404, 415, 413, 409, 400, and 503, each with a `ProblemDetail` body.

## 7. Tests

- [x] 7.1 Add `TodoItemImageControllerTest` with `@WebMvcTest`: the upload, the list,
      the 302 download with the `Location` header, the delete, and the five error
      statuses.
- [x] 7.2 Add the upload path, the download path, and the delete path to
      `TodoItemApiTest` against the real Garage container. The download test asserts
      the 302, then reads the `Location` URL and compares the bytes with the upload.
- [x] 7.3 Add an API test for the 20 image limit, the 415 content type, and the 413
      size limit.
- [x] 7.4 Add an API test that a `PUT` and a `PATCH` on the item keep the images.
- [x] 7.5 Add an API test that `DELETE /api/todos/{id}` removes the rows and the
      objects.
- [x] 7.6 Update the existing item tests for the new `images` field in the response.

## 8. Check

- [x] 8.1 Run `./mvnw verify` and check that every test passes.
- [x] 8.2 Run `docker compose up`, upload an image with `curl`, follow the redirect
      with `curl -L`, and check the bytes match. This also checks that
      `todo.images.public-endpoint` is reachable from outside the compose network.
- [x] 8.3 Run `openspec validate add-item-images --strict` and correct any error.
