package hu.wbstr.todo.item;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoItemController.class)
class TodoItemControllerTest {

    private static final UUID ID = UUID.fromString("6c8e4d0a-1f2b-4c3d-8e5f-0a1b2c3d4e5f");
    private static final UUID IMAGE_ID = UUID.fromString("1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d");
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-08-06T10:00:00+02:00");
    private static final OffsetDateTime UPDATED = OffsetDateTime.parse("2026-08-06T11:00:00+02:00");
    private static final OffsetDateTime DUE_AT = OffsetDateTime.parse("2026-09-01T12:00:00Z");
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TodoItemService service;

    private static TodoItemWithImages item(String title, String description, boolean done) {
        return new TodoItemWithImages(
                new TodoItem(ID, title, description, done, null, Priority.MEDIUM, Set.of(), CREATED, UPDATED, false),
                List.of());
    }

    private static TodoItemWithImages itemWithAnImage() {
        TodoItemWithImages item = item("Buy milk", null, false);
        return new TodoItemWithImages(
                item.item(),
                List.of(new TodoItemImage(
                        IMAGE_ID, ID, "image/png", 12L, "photo.png", "todos/%s/%s".formatted(ID, IMAGE_ID),
                        CREATED, false)));
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        given(service.create("Buy milk", "Two litres", null, null, null))
                .willReturn(item("Buy milk", "Two litres", false));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk", "description": "Two litres"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/todos/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.description").value("Two litres"))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.created").exists())
                .andExpect(jsonPath("$.updated").exists());
    }

    @Test
    void createWithoutDescriptionReturns201WithNullDescription() throws Exception {
        given(service.create("Buy milk", null, null, null, null)).willReturn(item("Buy milk", null, false));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void createPassesTheDueTimestampThePriorityAndTheTags() throws Exception {
        TodoItem created = new TodoItem(
                ID,
                "Buy milk",
                null,
                false,
                DUE_AT,
                Priority.HIGH,
                Set.of(new TodoItemTag("work")),
                CREATED,
                UPDATED,
                false);
        given(service.create("Buy milk", null, DUE_AT, Priority.HIGH, List.of("work")))
                .willReturn(new TodoItemWithImages(created, List.of()));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk", "dueAt": "2026-09-01T12:00:00Z", "priority": "HIGH",
                                 "tags": ["work"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.tags[0]").value("work"));
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void createWithMissingTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Two litres"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createWithAnUnknownPriorityReturns400() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk", "priority": "URGENT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void createWithABlankTagReturns400() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk", "tags": ["  "]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listReturnsAllItems() throws Exception {
        given(service.findAll(any())).willReturn(List.of(item("Buy milk", "Two litres", false)));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ID.toString()));
    }

    @Test
    void listReturnsAnEmptyArrayWhenNoItemExists() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listWithoutASortParameterQueriesTheCreatedTimestampNewestFirst() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos")).andExpect(status().isOk());

        assertThat(capturedQuery().sortField()).isEqualTo("created");
        assertThat(capturedQuery().direction()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listPassesTheFiltersAndTheSortToTheService() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos")
                        .param("priority", "HIGH", "LOW")
                        .param("tag", "work", "urgent")
                        .param("done", "false")
                        .param("dueAfter", "2026-01-01T00:00:00Z")
                        .param("dueBefore", "2026-02-01T00:00:00Z")
                        .param("sort", "dueAt,asc"))
                .andExpect(status().isOk());

        TodoItemQuery query = capturedQuery();
        assertThat(query.priorities()).containsExactly(Priority.HIGH, Priority.LOW);
        assertThat(query.tags()).containsExactly("work", "urgent");
        assertThat(query.done()).isFalse();
        assertThat(query.dueAfter()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(query.dueBefore()).isEqualTo(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        assertThat(query.sortField()).isEqualTo("dueAt");
        assertThat(query.direction()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listPassesTheSearchTextToTheService() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos").param("q", "invoice")).andExpect(status().isOk());

        assertThat(capturedQuery().searchText()).isEqualTo("invoice");
    }

    @Test
    void listWithABlankSearchTextPassesNullToTheService() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos").param("q", "   ")).andExpect(status().isOk());

        assertThat(capturedQuery().searchText()).isNull();
    }

    @Test
    void listTrimsTheOuterSpacesOfTheSearchText() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos").param("q", "  invoice  ")).andExpect(status().isOk());

        assertThat(capturedQuery().searchText()).isEqualTo("invoice");
    }

    @Test
    void listWithASearchTextAndNoSortQueriesTheRelevanceBestFirst() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos").param("q", "invoice")).andExpect(status().isOk());

        assertThat(capturedQuery().sortField()).isEqualTo("relevance");
        assertThat(capturedQuery().direction()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listWithABlankSearchTextAndNoSortQueriesTheCreatedTimestampNewestFirst() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos").param("q", " ")).andExpect(status().isOk());

        assertThat(capturedQuery().sortField()).isEqualTo("created");
        assertThat(capturedQuery().direction()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listWithARelevanceSortAndNoSearchTextReturns400() throws Exception {
        mockMvc.perform(get("/api/todos").param("sort", "relevance,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listWithASearchTextAboveTwoHundredAndFiftyFiveCharactersReturns400() throws Exception {
        mockMvc.perform(get("/api/todos").param("q", "x".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listWithASortFieldOnlyUsesTheAscendingDirection() throws Exception {
        given(service.findAll(any())).willReturn(List.of());

        mockMvc.perform(get("/api/todos").param("sort", "title")).andExpect(status().isOk());

        assertThat(capturedQuery().direction()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listWithAnUnknownPriorityReturns400() throws Exception {
        mockMvc.perform(get("/api/todos").param("priority", "URGENT"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listWithAnUnparsableDueBeforeReturns400() throws Exception {
        mockMvc.perform(get("/api/todos").param("dueBefore", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listWithAnUnknownSortFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/todos").param("sort", "colour,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listWithAnUnknownSortDirectionReturns400() throws Exception {
        mockMvc.perform(get("/api/todos").param("sort", "title,sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void findByIdReturnsTheItem() throws Exception {
        given(service.findById(ID)).willReturn(item("Buy milk", "Two litres", false));

        mockMvc.perform(get("/api/todos/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"));
    }

    @Test
    void findByIdReturnsTheImagesOfTheItem() throws Exception {
        given(service.findById(ID)).willReturn(itemWithAnImage());

        mockMvc.perform(get("/api/todos/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].id").value(IMAGE_ID.toString()))
                .andExpect(jsonPath("$.images[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.images[0].sizeBytes").value(12))
                .andExpect(jsonPath("$.images[0].fileName").value("photo.png"))
                .andExpect(jsonPath("$.images[0].downloadPath")
                        .value("/api/todos/" + ID + "/images/" + IMAGE_ID));
    }

    @Test
    void findByIdReturns404ForAnUnknownItem() throws Exception {
        given(service.findById(ID)).willThrow(new TodoItemNotFoundException(ID));

        mockMvc.perform(get("/api/todos/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void findByIdReturns400ForAnIdentifierThatIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/todos/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void replaceReturnsTheReplacedItem() throws Exception {
        given(service.replace(ID, "Buy bread", null, true, null, null, null))
                .willReturn(item("Buy bread", null, true));

        mockMvc.perform(put("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy bread", "done": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy bread"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void replaceWithBlankTitleReturns400() throws Exception {
        mockMvc.perform(put("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "  ", "done": true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void replaceReturns404ForAnUnknownItem() throws Exception {
        given(service.replace(eq(ID), any(), any(), eq(true), any(), any(), any()))
                .willThrow(new TodoItemNotFoundException(ID));

        mockMvc.perform(put("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy bread", "done": true}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchWithTheDoneFlagOnlyPassesNullForTheOtherFields() throws Exception {
        given(service.patch(ID, null, null, true, null, null, null))
                .willReturn(item("Buy milk", "Two litres", true));

        mockMvc.perform(patch("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.description").value("Two litres"))
                .andExpect(jsonPath("$.done").value(true));

        verify(service).patch(ID, null, null, true, null, null, null);
    }

    @Test
    void patchWithAnEmptyTagListPassesTheEmptyList() throws Exception {
        given(service.patch(ID, null, null, null, null, null, List.of()))
                .willReturn(item("Buy milk", null, false));

        mockMvc.perform(patch("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tags": []}"""))
                .andExpect(status().isOk());

        verify(service).patch(ID, null, null, null, null, null, List.of());
    }

    @Test
    void patchWithBlankTitleReturns400() throws Exception {
        mockMvc.perform(patch("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void patchReturns404ForAnUnknownItem() throws Exception {
        given(service.patch(ID, null, null, true, null, null, null))
                .willThrow(new TodoItemNotFoundException(ID));

        mockMvc.perform(patch("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done": true}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/todos/{id}", ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(service).should().delete(ID);
    }

    @Test
    void deleteReturns404ForAnUnknownItem() throws Exception {
        willThrow(new TodoItemNotFoundException(ID)).given(service).delete(ID);

        mockMvc.perform(delete("/api/todos/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verify(service, never()).findById(any());
    }

    private TodoItemQuery capturedQuery() {
        var captor = forClass(TodoItemQuery.class);
        verify(service).findAll(captor.capture());
        return captor.getValue();
    }
}
