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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TodoItemApiTest {

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
}
