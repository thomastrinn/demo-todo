## Context

The application is a Spring Boot 4.1 modulith. It uses Spring Data JDBC over
PostgreSQL, Liquibase for the schema, and Testcontainers for the tests. One module,
`hu.wbstr.todo.item`, holds the whole todo item capability. `TodoItem` is an aggregate
root. It owns its tags through a `@MappedCollection`.

The change adds images to a todo item. An image has bytes and metadata. PostgreSQL is a
poor place for the bytes, so the bytes go to an S3-compatible object store. The user
chose Garage as that store, for `compose.yaml` and for the tests.

The project has no object store, no S3 client, and no multipart endpoint today.

## Goals / Non-Goals

**Goals:**

- Add, list, download, and delete the images of a todo item.
- Keep the image bytes out of PostgreSQL.
- Run the store locally with `compose.yaml` and in the tests with Testcontainers.
- Keep the existing item endpoints and the item aggregate unchanged in behaviour.

**Non-Goals:**

- Image processing: no thumbnails, no resizing, no format conversion, no EXIF strip.
- A presigned upload. The upload stays multipart through the API, so the server keeps
  the size check, the content type check, and the true byte count.
- A CORS rule on the bucket for a browser client that reads the redirect target with
  `fetch`.
- Deduplication of identical uploads.
- A background job that finds orphaned objects.
- Authentication or per-user access control. The API has none today.

## Decisions

### Use the AWS SDK for Java v2 S3 client

The application talks to Garage with `software.amazon.awssdk:s3`, pinned through
`software.amazon.awssdk:bom`. Spring Boot 4.1 does not manage the AWS SDK version, so
the BOM goes into `dependencyManagement`.

The `S3Client` bean needs three settings for Garage:

- `endpointOverride` with the Garage S3 API address.
- `forcePathStyle(true)`, because Garage serves a virtual-host style bucket only under
  a configured `root_domain`.
- `StaticCredentialsProvider` with the access key and the secret key, and the region
  `garage`.

The client also needs `requestChecksumCalculation` and `responseChecksumValidation` set
to `WHEN_REQUIRED`. The default (`WHEN_SUPPORTED`) signs the payload in `aws-chunked`
frames, and Garage answers `Invalid payload signature` to that.

A `@ConfigurationProperties("todo.images")` record holds the endpoint, the public
endpoint, the region, the bucket, the access key, the secret key, and the presign
lifetime.

Alternatives:

- **Spring Cloud AWS (`io.awspring.cloud`)**: it adds auto-configuration and a
  `S3Template`, but its release train follows Spring Boot with a lag. Spring Boot 4.1
  is new. One `S3Client` bean is a few lines. Rejected.
- **The MinIO Java client**: a second vendor client for the same protocol, with a
  smaller ecosystem. Rejected.

### Store the bytes in Garage, run it single-node

`compose.yaml` gains a `garage` service on the image `dxflrs/garage:v2.3.0`. The
service runs `/garage server --single-node --default-bucket` and reads the environment
variables `GARAGE_DEFAULT_ACCESS_KEY`, `GARAGE_DEFAULT_SECRET_KEY`, and
`GARAGE_DEFAULT_BUCKET`. That mode creates the layout, the key, and the bucket at
start, so no `docker exec` step follows the start.

Garage still needs a `garage.toml`. The repository holds a minimal one under
`garage/garage.toml`: `metadata_dir`, `data_dir`, `db_engine = "sqlite"`,
`replication_factor = 1`, `rpc_bind_addr`, a fixed `rpc_secret`, and an `[s3_api]`
block that binds port 3900 and sets `s3_region = "garage"`. The `rpc_secret` is a local
development value, not a production secret.

The image sets no entrypoint, so the command is the whole line
`/garage server --single-node --default-bucket`. Garage refuses a
`GARAGE_DEFAULT_SECRET_KEY` below 16 characters.

The tests use the same image. `TestcontainersConfiguration` gains a
`GenericContainer` bean that exposes port 3900, copies the same `garage.toml` into
`/etc/garage.toml`, and waits for the S3 API port. Testcontainers has no Garage module
and Spring Boot has no `@ServiceConnection` for it, so a `DynamicPropertyRegistrar`
bean binds `todo.images.endpoint` to the mapped port. Boot reuses one container for the
whole suite, because the container is a singleton bean of the shared test
configuration.

Alternative: **LocalStack**, which has a Testcontainers module and `@ServiceConnection`
support. The user chose Garage, and Garage is also the local runtime, so the tests run
against the same server as the developer. Rejected.

### Make the image its own aggregate, not a mapped collection of the item

`TodoItemImage` is a Spring Data JDBC aggregate root with its own repository over the
table `todo_item_image`. It holds `todoItemId` as a plain column.

The tags use `@MappedCollection` inside `TodoItem`. The images must not. Spring Data
JDBC rewrites a mapped collection on every save of the root, so every `PUT` and every
`PATCH` on an item would delete and re-insert the image rows. It would also pull the
object store into the item save path. A separate aggregate keeps `POST /api/todos` and
the `PUT`, `PATCH` handlers untouched.

Trade-off: the item aggregate no longer guarantees the link. The database enforces it
with a foreign key with `ON DELETE CASCADE`, and the service checks that the item
exists before it stores an image.

### Read the images with one extra query per response

`TodoItemResponse` gains an `images` array, so `GET /api/todos` needs the images of
every listed item. The service reads them with one
`findByTodoItemIdInOrderByCreatedAscIdAsc` call and groups them by item id in memory.
There is no query per item. The count is bounded, because an item holds at most 20
images.

### Redirect the download to a presigned URL

`GET /api/todos/{id}/images/{imageId}` does not carry the bytes. It reads the metadata
row, builds a presigned GET URL, and answers 302 with that URL in the `Location`
header. The client then reads the object from Garage directly. The image bytes never
pass through the application on the read path, which is the reason to hold them in an
object store at all.

The endpoint path stays stable, so `TodoItemImageResponse.downloadPath` and the
`images` array of a todo item response hold a URL that never expires. Only the redirect
target expires. A client that keeps an item response for an hour still gets a working
download, because every call to the endpoint mints a fresh URL.

An `S3Presigner` bean builds the URL. `todo.images.presign-ttl` sets the lifetime, with
5 minutes as the default. The presign request also sets `responseContentType` and an
inline `responseContentDisposition` with the original file name, so the redirect target
answers with the same headers a proxy would have set.

The application reads the metadata row before it builds the URL. An unknown image, or
an image of a different todo item, therefore still answers 404 without a call to
Garage.

The presigner needs the address that the **client** can reach. That is not always the
address the application uses. `todo.images.public-endpoint` holds it, and defaults to
`todo.images.endpoint`. Both are `localhost:3900` today, because the application runs
on the host in `compose.yaml` and in the tests. The two values diverge as soon as the
application itself runs in a container next to Garage.

Alternatives:

- **Stream the bytes through the API**: one code path, no expiry, and no exposed store
  address. It also makes the application a proxy for every byte, which gives up the
  main reason to use an object store. Rejected.
- **Put a ready presigned URL in every item response**: it removes the redirect hop,
  but the URL then expires inside a cached or stored response. Rejected.
- **Presign the upload too**: the client would write to the bucket directly, so the
  application would never see the bytes. It could not enforce the 10 MB limit or the
  `image/*` check, and it could not record the true size. A POST policy can carry those
  conditions, but it turns one request into three, and the metadata row is correct only
  if the client returns for the confirm step. Rejected. The upload stays multipart
  through the API.

### Order the writes so that no visible row loses its object

- **Upload**: write the object first, then insert the metadata row. When the insert
  fails, delete the object and rethrow. A failure between the two steps leaves an
  invisible object, never a row without bytes.
- **Delete an image**: delete the row first, then the object. The same rule holds.
- **Delete an item**: read the storage keys, delete the item (the foreign key cascade
  removes the rows), then delete the objects.

An orphaned object is invisible to a client and wastes only disk. A row without an
object returns 500 on every download, so the order above is the safe one.

### Map the failures to the statuses the spec names

`TodoItemImageExceptionHandler` (a `@RestControllerAdvice` next to the existing
`TodoItemExceptionHandler`) maps:

| Condition | Exception | Status |
| --- | --- | --- |
| Unknown item or unknown image | `TodoItemImageNotFoundException`, `TodoItemNotFoundException` | 404 |
| Content type not `image/*` | `UnsupportedImageTypeException` | 415 |
| File above 10 MB | `MaxUploadSizeExceededException` | 413 |
| Item already holds 20 images | `TooManyImagesException` | 409 |
| Missing `file` part | `MissingServletRequestPartException` | 400 |
| Garage does not answer | `ImageStoreUnavailableException` (wraps `SdkException`) | 503 |

The size limit lives in `application.properties`
(`spring.servlet.multipart.max-file-size=10MB`,
`spring.servlet.multipart.max-request-size=11MB`), so the container rejects the body
before the controller reads it.

### Keep the image code inside the `item` module

The image types live in `hu.wbstr.todo.item`. A new Spring Modulith module for the
object store would need a published API and a port interface for one caller.
`TodoItemImageStore` wraps the `S3Client` and the `S3Presigner` directly, with `put`,
`presignGet`, and `delete`.
`ModularityTests` keeps passing, because no new module boundary appears.

### Name the objects after the item and the image

The storage key is `todos/{todoItemId}/{imageId}`. It is unique, it groups the objects
of one item, and it makes a manual inspection of the bucket readable.

## Risks / Trade-offs

- **The `--single-node --default-bucket` flags may not exist in the pinned Garage
  version** → The first implementation task starts the container and checks the flags.
  If they are absent, the fallback is a start-up step that runs `garage layout assign`,
  `garage layout apply`, `garage key import`, `garage bucket create`, and
  `garage bucket allow` through `execInContainer`, and a matching `command` override in
  `compose.yaml`.
- **The Garage container slows the test suite** → One shared container serves every
  test class, the same way the PostgreSQL container does today.
- **A 10 MB upload holds a request thread for the whole transfer** → Acceptable at this
  scale. Only the upload path has this cost, because the download path redirects.
- **A presigned URL works for anybody who holds it, until it expires** → The lifetime
  is 5 minutes and the URL names one object. The API has no authentication today, so
  the URL grants nothing that the endpoint does not already grant to everybody.
- **A browser that reads the redirect target with `fetch` needs a CORS rule on the
  bucket** → An `<img src>` and a plain navigation follow the redirect without CORS. A
  browser client that reads the bytes with `fetch` or `XMLHttpRequest` needs a CORS
  rule on the Garage bucket. Setting that rule is out of scope here.
- **The public endpoint must be reachable by the client** → `todo.images.public-endpoint`
  holds it. A deployment that gets it wrong answers 302 with a `Location` that the
  client cannot open. Task 8.2 checks this against the running compose stack.
- **An object can outlive its row when the object delete fails** → The leak is
  invisible to a client. A cleanup job is out of scope.
- **The API has no authentication, so anybody can download any image** → The same is
  already true of every item endpoint. This change adds no new exposure model.
- **The `images` field grows every item response** → The entries hold metadata only,
  and an item holds at most 20 of them.

## Migration Plan

1. The Liquibase changeset `003-add-item-images.yaml` creates `todo_item_image`. It
   adds a table only, so it needs no data migration and no downtime.
2. The new `images` field is additive. An older client that ignores it keeps working.
3. A deployment needs a reachable Garage and the `todo.images.*` properties. The client
   must be able to open `todo.images.public-endpoint`.
   Without them the application fails at start, which is the wanted behaviour: an
   upload must not appear to work and then lose the bytes.
4. Rollback: drop the new endpoints and the changeset. No existing data changes.

## Open Questions

None. Presigned URLs, thumbnails, and an orphan cleanup job are out of scope and need
no decision now.
