package hu.wbstr.todo.item;

import hu.wbstr.todo.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TodoItemApiTest {

    private static final OffsetDateTime FEBRUARY = OffsetDateTime.parse("2030-02-15T10:00:00Z");
    private static final OffsetDateTime MARCH = OffsetDateTime.parse("2030-03-15T10:00:00Z");
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};

    @LocalServerPort
    int port;

    @Autowired
    S3Client s3Client;

    @Autowired
    TodoItemImageProperties imageProperties;

    @Autowired
    TodoItemImageRepository imageRepository;

    private RestClient client;

    @BeforeEach
    void createClient() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                })
                .build();
    }

    @Test
    void walksTheWholeLifecycleOfATodoItem() {
        ResponseEntity<TodoItemResponse> created = client.post()
                .uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Buy milk", "description": "Two litres"}""")
                .retrieve()
                .toEntity(TodoItemResponse.class);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        TodoItemResponse item = created.getBody();
        assertThat(item).isNotNull();
        assertThat(item.title()).isEqualTo("Buy milk");
        assertThat(item.description()).isEqualTo("Two litres");
        assertThat(item.done()).isFalse();
        assertThat(item.created()).isEqualTo(item.updated());
        assertThat(created.getHeaders().getLocation()).isEqualTo(URI.create("/api/todos/" + item.id()));

        String itemPath = "/api/todos/" + item.id();

        assertThat(client.get().uri("/api/todos").retrieve().body(TodoItemResponse[].class))
                .extracting(TodoItemResponse::id)
                .contains(item.id());

        assertThat(client.get().uri(itemPath).retrieve().body(TodoItemResponse.class)).isEqualTo(item);

        ResponseEntity<TodoItemResponse> replaced = client.put()
                .uri(itemPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Buy bread", "done": false}""")
                .retrieve()
                .toEntity(TodoItemResponse.class);

        assertThat(replaced.getStatusCode().value()).isEqualTo(200);
        assertThat(replaced.getBody().title()).isEqualTo("Buy bread");
        assertThat(replaced.getBody().description()).isNull();
        assertThat(replaced.getBody().created()).isEqualTo(item.created());
        assertThat(replaced.getBody().updated()).isAfterOrEqualTo(item.updated());

        ResponseEntity<TodoItemResponse> patched = client.patch()
                .uri(itemPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"done": true}""")
                .retrieve()
                .toEntity(TodoItemResponse.class);

        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        assertThat(patched.getBody().done()).isTrue();
        assertThat(patched.getBody().title()).isEqualTo("Buy bread");

        ResponseEntity<Void> deleted = client.delete().uri(itemPath).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> afterDelete = client.get().uri(itemPath).retrieve().toEntity(String.class);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);
        assertThat(afterDelete.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void createsAnItemWithTheDueTimestampThePriorityAndTheTags() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "dueAt": "2030-02-15T10:00:00Z", "priority": "HIGH",
                 "tags": ["work", "urgent"]}""");

        assertThat(created.dueAt().toInstant()).isEqualTo(FEBRUARY.toInstant());
        assertThat(created.priority()).isEqualTo(Priority.HIGH);
        assertThat(created.tags()).containsExactly("urgent", "work");

        TodoItemResponse found = client.get()
                .uri("/api/todos/" + created.id())
                .retrieve()
                .body(TodoItemResponse.class);
        assertThat(found).isEqualTo(created);
    }

    @Test
    void createsAnItemWithoutTheOptionalFields() {
        TodoItemResponse created = create("""
                {"title": "Buy milk"}""");

        assertThat(created.dueAt()).isNull();
        assertThat(created.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(created.tags()).isEmpty();
        assertThat(created.images()).isEmpty();
    }

    @Test
    void collapsesADuplicateTag() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "tags": ["work", "work"]}""");

        assertThat(created.tags()).containsExactly("work");
    }

    @Test
    void replaceClearsTheDueTimestampAndTheTagsAndResetsThePriority() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "dueAt": "2030-02-15T10:00:00Z", "priority": "HIGH",
                 "tags": ["work"]}""");

        TodoItemResponse replaced = client.put()
                .uri("/api/todos/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Buy bread", "done": false}""")
                .retrieve()
                .body(TodoItemResponse.class);

        assertThat(replaced.dueAt()).isNull();
        assertThat(replaced.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(replaced.tags()).isEmpty();
    }

    @Test
    void replaceSetsANewTagList() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "tags": ["work", "urgent"]}""");

        TodoItemResponse replaced = client.put()
                .uri("/api/todos/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Buy milk", "done": false, "tags": ["home"]}""")
                .retrieve()
                .body(TodoItemResponse.class);

        assertThat(replaced.tags()).containsExactly("home");
    }

    @Test
    void patchChangesTheDueTimestampOnly() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "priority": "HIGH", "tags": ["work"]}""");

        TodoItemResponse patched = patch(created.id(), """
                {"dueAt": "2030-03-15T10:00:00Z"}""");

        assertThat(patched.dueAt().toInstant()).isEqualTo(MARCH.toInstant());
        assertThat(patched.priority()).isEqualTo(Priority.HIGH);
        assertThat(patched.tags()).containsExactly("work");
    }

    @Test
    void patchChangesThePriorityOnly() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "dueAt": "2030-03-15T10:00:00Z", "tags": ["work"]}""");

        TodoItemResponse patched = patch(created.id(), """
                {"priority": "LOW"}""");

        assertThat(patched.priority()).isEqualTo(Priority.LOW);
        assertThat(patched.dueAt().toInstant()).isEqualTo(MARCH.toInstant());
        assertThat(patched.tags()).containsExactly("work");
    }

    @Test
    void patchReplacesTheTagsOnly() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "dueAt": "2030-03-15T10:00:00Z", "priority": "HIGH",
                 "tags": ["work", "urgent"]}""");

        TodoItemResponse patched = patch(created.id(), """
                {"tags": ["home"]}""");

        assertThat(patched.tags()).containsExactly("home");
        assertThat(patched.dueAt().toInstant()).isEqualTo(MARCH.toInstant());
        assertThat(patched.priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void patchWithAnEmptyTagListClearsTheTags() {
        TodoItemResponse created = create("""
                {"title": "Buy milk", "tags": ["work", "urgent"]}""");

        TodoItemResponse patched = patch(created.id(), """
                {"tags": []}""");

        assertThat(patched.tags()).isEmpty();
    }

    @Test
    void filtersByOnePriority() {
        String group = "filter-one-priority";
        TodoItemResponse high = create(taggedItem("Buy milk", "\"priority\": \"HIGH\"", group));
        create(taggedItem("Buy bread", "\"priority\": \"LOW\"", group));

        assertThat(ids(list("?tag=" + group + "&priority=HIGH"))).containsExactly(high.id());
    }

    @Test
    void filtersByTwoPriorities() {
        String group = "filter-two-priorities";
        TodoItemResponse high = create(taggedItem("Buy milk", "\"priority\": \"HIGH\"", group));
        TodoItemResponse low = create(taggedItem("Buy bread", "\"priority\": \"LOW\"", group));
        create(taggedItem("Buy eggs", "\"priority\": \"MEDIUM\"", group));

        assertThat(ids(list("?tag=" + group + "&priority=HIGH&priority=LOW")))
                .containsExactlyInAnyOrder(high.id(), low.id());
    }

    @Test
    void filtersByOneTag() {
        String group = "filter-one-tag";
        TodoItemResponse tagged = create(taggedItem("Buy milk", null, group, "work"));
        create(taggedItem("Buy bread", null, group, "home"));

        assertThat(ids(list("?tag=" + group + "&tag=work"))).containsExactly(tagged.id());
    }

    @Test
    void filtersByTwoTags() {
        String group = "filter-two-tags";
        TodoItemResponse both = create(taggedItem("Buy milk", null, group, "work", "urgent"));
        create(taggedItem("Buy bread", null, group, "work"));

        assertThat(ids(list("?tag=" + group + "&tag=work&tag=urgent"))).containsExactly(both.id());
    }

    @Test
    void filtersByTheDoneFlag() {
        String group = "filter-done";
        TodoItemResponse open = create(taggedItem("Buy milk", null, group));
        TodoItemResponse closed = create(taggedItem("Buy bread", null, group));
        patch(closed.id(), """
                {"done": true}""");

        assertThat(ids(list("?tag=" + group + "&done=false"))).containsExactly(open.id());
        assertThat(ids(list("?tag=" + group + "&done=true"))).containsExactly(closed.id());
    }

    @Test
    void filtersByADueWindowAndDropsAnItemWithoutADueTimestamp() {
        String group = "filter-due-window";
        TodoItemResponse inside = create(taggedItem("Buy milk", "\"dueAt\": \"2030-02-15T10:00:00Z\"", group));
        create(taggedItem("Buy bread", "\"dueAt\": \"2030-04-15T10:00:00Z\"", group));
        create(taggedItem("Buy eggs", null, group));

        assertThat(ids(list("?tag=" + group + "&dueAfter=2030-01-15T10:00:00Z&dueBefore=2030-03-15T10:00:00Z")))
                .containsExactly(inside.id());
    }

    @Test
    void sortsByTheDueTimestampWithTheNullValuesLast() {
        String group = "sort-due-at";
        TodoItemResponse early = create(taggedItem("Buy milk", "\"dueAt\": \"2030-01-15T10:00:00Z\"", group));
        TodoItemResponse late = create(taggedItem("Buy bread", "\"dueAt\": \"2030-03-15T10:00:00Z\"", group));
        TodoItemResponse without = create(taggedItem("Buy eggs", null, group));

        assertThat(ids(list("?tag=" + group + "&sort=dueAt,asc")))
                .containsExactly(early.id(), late.id(), without.id());
        assertThat(ids(list("?tag=" + group + "&sort=dueAt,desc")))
                .containsExactly(late.id(), early.id(), without.id());
    }

    @Test
    void sortsByThePriorityInBothDirections() {
        String group = "sort-priority";
        TodoItemResponse low = create(taggedItem("Buy milk", "\"priority\": \"LOW\"", group));
        TodoItemResponse medium = create(taggedItem("Buy bread", "\"priority\": \"MEDIUM\"", group));
        TodoItemResponse high = create(taggedItem("Buy eggs", "\"priority\": \"HIGH\"", group));

        assertThat(ids(list("?tag=" + group + "&sort=priority,asc")))
                .containsExactly(low.id(), medium.id(), high.id());
        assertThat(ids(list("?tag=" + group + "&sort=priority,desc")))
                .containsExactly(high.id(), medium.id(), low.id());
    }

    @Test
    void returnsTheNewestItemFirstByDefault() {
        String group = "default-order";
        TodoItemResponse first = create(taggedItem("Buy milk", null, group));
        TodoItemResponse second = create(taggedItem("Buy bread", null, group));
        TodoItemResponse third = create(taggedItem("Buy eggs", null, group));

        assertThat(ids(list("?tag=" + group))).containsExactly(third.id(), second.id(), first.id());
    }

    @Test
    void searchesTheTitleAndTheDescriptionByAWord() {
        String group = "search-word";
        TodoItemResponse title = create(taggedItem("Pay the invoice", null, group));
        TodoItemResponse description =
                create(taggedItem("Buy milk", "\"description\": \"File the receipt\"", group));
        create(taggedItem("Walk the dog", null, group));

        assertThat(ids(list("?tag=" + group + "&q=invoice"))).containsExactly(title.id());
        assertThat(ids(list("?tag=" + group + "&q=receipt"))).containsExactly(description.id());
    }

    @Test
    void combinesTheSearchAndAFilter() {
        String group = "search-filter";
        TodoItemResponse high = create(taggedItem("Pay the invoice", "\"priority\": \"HIGH\"", group));
        create(taggedItem("Pay the invoice twice", "\"priority\": \"LOW\"", group));

        assertThat(ids(list("?tag=" + group + "&q=invoice&priority=HIGH"))).containsExactly(high.id());
    }

    @Test
    void putsTheTitleHitFirstInARelevanceSort() {
        String group = "search-relevance";
        TodoItemResponse title = create(taggedItem("Pay the invoice", null, group));
        TodoItemResponse description =
                create(taggedItem("Buy milk", "\"description\": \"Pay the invoice\"", group));

        assertThat(ids(list("?tag=" + group + "&q=invoice&sort=relevance,desc")))
                .containsExactly(title.id(), description.id());
    }

    @Test
    void returnsEveryItemForABlankSearchText() {
        String group = "search-blank";
        TodoItemResponse first = create(taggedItem("Pay the invoice", null, group));
        TodoItemResponse second = create(taggedItem("Buy milk", null, group));

        assertThat(ids(list("?tag=" + group + "&q= "))).containsExactly(second.id(), first.id());
    }

    @Test
    void rejectsABlankTitle() {
        ResponseEntity<String> response = client.post()
                .uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "   "}""")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsAnIdentifierThatIsNotAUuid() {
        ResponseEntity<String> response = client.get()
                .uri("/api/todos/not-a-uuid")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void rejectsAnUnknownPriority() {
        assertBadRequest(post("""
                {"title": "Buy milk", "priority": "URGENT"}"""));
    }

    @Test
    void rejectsABlankTag() {
        assertBadRequest(post("""
                {"title": "Buy milk", "tags": ["  "]}"""));
    }

    @Test
    void rejectsATagAboveFiftyCharacters() {
        assertBadRequest(post("{\"title\": \"Buy milk\", \"tags\": [\"" + "x".repeat(51) + "\"]}"));
    }

    @Test
    void rejectsMoreThanTwentyTags() {
        String tags = IntStream.range(0, 21).mapToObj(index -> "\"tag" + index + "\"").collect(Collectors.joining(","));
        assertBadRequest(post("{\"title\": \"Buy milk\", \"tags\": [" + tags + "]}"));
    }

    @Test
    void rejectsAnUnknownPriorityFilter() {
        ResponseEntity<String> response = client.get()
                .uri("/api/todos?priority=URGENT")
                .retrieve()
                .toEntity(String.class);

        assertBadRequest(response);
    }

    @Test
    void rejectsAnUnknownSortField() {
        ResponseEntity<String> response = client.get()
                .uri("/api/todos?sort=colour,asc")
                .retrieve()
                .toEntity(String.class);

        assertBadRequest(response);
    }

    @Test
    void walksTheWholeLifecycleOfAnImage() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");
        assertThat(item.images()).isEmpty();

        ResponseEntity<TodoItemImageResponse> uploaded =
                upload(item.id(), "photo.png", MediaType.IMAGE_PNG, PNG_BYTES, TodoItemImageResponse.class);

        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        TodoItemImageResponse image = uploaded.getBody();
        assertThat(image).isNotNull();
        assertThat(image.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(image.sizeBytes()).isEqualTo(PNG_BYTES.length);
        assertThat(image.fileName()).isEqualTo("photo.png");
        assertThat(image.created()).isNotNull();
        String imagePath = "/api/todos/" + item.id() + "/images/" + image.id();
        assertThat(image.downloadPath()).isEqualTo(imagePath);
        assertThat(uploaded.getHeaders().getLocation()).isEqualTo(URI.create(imagePath));

        assertThat(images(item.id())).extracting(TodoItemImageResponse::id).containsExactly(image.id());
        assertThat(findItem(item.id()).images()).containsExactly(image);

        ResponseEntity<Void> download = client.get().uri(imagePath).retrieve().toBodilessEntity();
        assertThat(download.getStatusCode().value()).isEqualTo(302);
        URI presigned = download.getHeaders().getLocation();
        assertThat(presigned).isNotNull();

        ResponseEntity<byte[]> object = RestClient.create().get().uri(presigned).retrieve().toEntity(byte[].class);
        assertThat(object.getStatusCode().value()).isEqualTo(200);
        assertThat(object.getBody()).isEqualTo(PNG_BYTES);
        assertThat(object.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);

        // Every call mints a new URL, and the path stays valid.
        URI second = client.get().uri(imagePath).retrieve().toBodilessEntity().getHeaders().getLocation();
        assertThat(RestClient.create().get().uri(second).retrieve().toEntity(byte[].class).getBody())
                .isEqualTo(PNG_BYTES);

        String storageKey = imageRepository.findById(image.id()).orElseThrow().storageKey();

        ResponseEntity<Void> deleted = client.delete().uri(imagePath).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
        assertThat(client.get().uri(imagePath).retrieve().toEntity(String.class).getStatusCode().value())
                .isEqualTo(404);
        assertThat(objectExists(storageKey)).isFalse();
        assertThat(findItem(item.id()).images()).isEmpty();
    }

    @Test
    void uploadsASecondImageAndListsBothOldestFirst() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");

        TodoItemImageResponse first = uploadPng(item.id(), "first.png");
        TodoItemImageResponse second = uploadPng(item.id(), "second.png");

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(images(item.id()))
                .extracting(TodoItemImageResponse::id)
                .containsExactly(first.id(), second.id());
    }

    @Test
    void uploadsAnImageWithoutAFileName() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");

        TodoItemImageResponse image = uploadPng(item.id(), null);

        assertThat(image.fileName()).isNull();
    }

    @Test
    void uploadToAnUnknownItemReturns404AndStoresNoObject() {
        UUID unknown = UUID.randomUUID();

        assertProblem(upload(unknown, "photo.png", MediaType.IMAGE_PNG, PNG_BYTES, String.class), 404);

        assertThat(imageRepository.countByTodoItemId(unknown)).isZero();
    }

    @Test
    void listAndDownloadForAnUnknownItemReturn404() {
        UUID unknown = UUID.randomUUID();

        assertThat(client.get()
                        .uri("/api/todos/" + unknown + "/images")
                        .retrieve()
                        .toEntity(String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
        assertThat(client.get()
                        .uri("/api/todos/" + unknown + "/images/" + UUID.randomUUID())
                        .retrieve()
                        .toEntity(String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    @Test
    void anImageOfAnotherItemIsNotVisibleThroughThisItem() {
        TodoItemResponse owner = create("""
                {"title": "Fix the tap"}""");
        TodoItemResponse other = create("""
                {"title": "Buy milk"}""");
        TodoItemImageResponse image = uploadPng(owner.id(), "photo.png");

        String foreignPath = "/api/todos/" + other.id() + "/images/" + image.id();
        assertThat(client.get().uri(foreignPath).retrieve().toEntity(String.class).getStatusCode().value())
                .isEqualTo(404);
        assertThat(client.delete().uri(foreignPath).retrieve().toEntity(String.class).getStatusCode().value())
                .isEqualTo(404);
        assertThat(images(owner.id())).hasSize(1);
    }

    @Test
    void uploadPastTheImageLimitReturns409() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");
        for (int index = 0; index < TodoItemImageService.MAX_IMAGES; index++) {
            uploadPng(item.id(), "photo" + index + ".png");
        }

        assertProblem(
                upload(item.id(), "past-the-limit.png", MediaType.IMAGE_PNG, PNG_BYTES, String.class), 409);

        assertThat(images(item.id())).hasSize(TodoItemImageService.MAX_IMAGES);
    }

    @Test
    void uploadOfANonImageReturns415AndStoresNoImage() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");

        assertProblem(
                upload(item.id(), "invoice.pdf", MediaType.APPLICATION_PDF, PNG_BYTES, String.class), 415);

        assertThat(images(item.id())).isEmpty();
    }

    @Test
    void uploadAboveTheSizeLimitReturns413AndStoresNoImage() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");

        assertProblem(
                upload(item.id(), "huge.png", MediaType.IMAGE_PNG, new byte[11 * 1024 * 1024], String.class), 413);

        assertThat(images(item.id())).isEmpty();
    }

    @Test
    void uploadWithoutTheFilePartReturns400() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("other", "value");

        ResponseEntity<String> response = client.post()
                .uri("/api/todos/" + item.id() + "/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void replaceAndPatchKeepTheImages() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap", "tags": ["home"]}""");
        TodoItemImageResponse first = uploadPng(item.id(), "first.png");
        TodoItemImageResponse second = uploadPng(item.id(), "second.png");

        TodoItemResponse replaced = client.put()
                .uri("/api/todos/" + item.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Fix the sink", "done": true}""")
                .retrieve()
                .body(TodoItemResponse.class);

        assertThat(replaced.images()).containsExactly(first, second);

        TodoItemResponse patched = patch(item.id(), """
                {"title": "Fix the shower"}""");

        assertThat(patched.images()).containsExactly(first, second);
        assertThat(images(item.id())).containsExactly(first, second);
    }

    @Test
    void deletingTheItemRemovesTheRowsAndTheObjects() {
        TodoItemResponse item = create("""
                {"title": "Fix the tap"}""");
        TodoItemImageResponse first = uploadPng(item.id(), "first.png");
        TodoItemImageResponse second = uploadPng(item.id(), "second.png");
        String firstKey = imageRepository.findById(first.id()).orElseThrow().storageKey();
        String secondKey = imageRepository.findById(second.id()).orElseThrow().storageKey();

        ResponseEntity<Void> deleted = client.delete()
                .uri("/api/todos/" + item.id())
                .retrieve()
                .toBodilessEntity();

        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
        assertThat(imageRepository.countByTodoItemId(item.id())).isZero();
        assertThat(objectExists(firstKey)).isFalse();
        assertThat(objectExists(secondKey)).isFalse();
    }

    private <T> ResponseEntity<T> upload(
            UUID itemId, String fileName, MediaType contentType, byte[] bytes, Class<T> responseType) {
        // A part needs a filename attribute to stay a file part. An empty one is how a
        // client sends a file without a name.
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName != null ? fileName : "";
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(contentType);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        return client.post()
                .uri("/api/todos/" + itemId + "/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private TodoItemImageResponse uploadPng(UUID itemId, String fileName) {
        ResponseEntity<TodoItemImageResponse> response =
                upload(itemId, fileName, MediaType.IMAGE_PNG, PNG_BYTES, TodoItemImageResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody();
    }

    private static void assertProblem(ResponseEntity<String> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private List<TodoItemImageResponse> images(UUID itemId) {
        return List.of(client.get()
                .uri("/api/todos/" + itemId + "/images")
                .retrieve()
                .body(TodoItemImageResponse[].class));
    }

    private TodoItemResponse findItem(UUID itemId) {
        return client.get().uri("/api/todos/" + itemId).retrieve().body(TodoItemResponse.class);
    }

    private boolean objectExists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(imageProperties.bucket())
                    .key(storageKey)
                    .build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        }
    }

    private static String taggedItem(String title, String extraField, String group, String... tags) {
        String tagList = Arrays.stream(tags).map(tag -> "\"" + tag + "\"").collect(Collectors.joining(","));
        String separator = tagList.isEmpty() ? "" : ",";
        return "{\"title\": \"" + title + "\", \"done\": false, "
                + (extraField != null ? extraField + ", " : "")
                + "\"tags\": [\"" + group + "\"" + separator + tagList + "]}";
    }

    private TodoItemResponse create(String body) {
        ResponseEntity<TodoItemResponse> response = client.post()
                .uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(TodoItemResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody();
    }

    private ResponseEntity<String> post(String body) {
        return client.post()
                .uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private TodoItemResponse patch(UUID id, String body) {
        ResponseEntity<TodoItemResponse> response = client.patch()
                .uri("/api/todos/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(TodoItemResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private List<TodoItemResponse> list(String query) {
        return List.of(client.get().uri("/api/todos" + query).retrieve().body(TodoItemResponse[].class));
    }

    private static List<UUID> ids(List<TodoItemResponse> items) {
        return items.stream().map(TodoItemResponse::id).toList();
    }

    private static void assertBadRequest(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
