package hu.wbstr.todo.item;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-08-06T10:00:00+02:00");
    private static final OffsetDateTime UPDATED = OffsetDateTime.parse("2026-08-06T11:00:00+02:00");
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TodoItemService service;

    private static TodoItem item(String title, String description, boolean done) {
        return new TodoItem(ID, title, description, done, CREATED, UPDATED, false);
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        given(service.create("Buy milk", "Two litres")).willReturn(item("Buy milk", "Two litres", false));

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
                .andExpect(jsonPath("$.created").exists())
                .andExpect(jsonPath("$.updated").exists());
    }

    @Test
    void createWithoutDescriptionReturns201WithNullDescription() throws Exception {
        given(service.create("Buy milk", null)).willReturn(item("Buy milk", null, false));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
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
    void listReturnsAllItems() throws Exception {
        given(service.findAll()).willReturn(List.of(item("Buy milk", "Two litres", false)));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ID.toString()));
    }

    @Test
    void listReturnsAnEmptyArrayWhenNoItemExists() throws Exception {
        given(service.findAll()).willReturn(List.of());

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void findByIdReturnsTheItem() throws Exception {
        given(service.findById(ID)).willReturn(item("Buy milk", "Two litres", false));

        mockMvc.perform(get("/api/todos/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"));
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
        given(service.replace(ID, "Buy bread", null, true)).willReturn(item("Buy bread", null, true));

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
        given(service.replace(eq(ID), any(), any(), eq(true))).willThrow(new TodoItemNotFoundException(ID));

        mockMvc.perform(put("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy bread", "done": true}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchWithTheDoneFlagOnlyPassesNullForTheOtherFields() throws Exception {
        given(service.patch(ID, null, null, true)).willReturn(item("Buy milk", "Two litres", true));

        mockMvc.perform(patch("/api/todos/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.description").value("Two litres"))
                .andExpect(jsonPath("$.done").value(true));

        verify(service).patch(ID, null, null, true);
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
        given(service.patch(ID, null, null, true)).willThrow(new TodoItemNotFoundException(ID));

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
}
