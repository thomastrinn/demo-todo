package hu.wbstr.todo.item;

import hu.wbstr.todo.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

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

    @LocalServerPort
    int port;

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
