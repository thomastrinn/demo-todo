package hu.wbstr.todo.item;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoItemImageController.class)
class TodoItemImageControllerTest {

    private static final UUID ID = UUID.fromString("6c8e4d0a-1f2b-4c3d-8e5f-0a1b2c3d4e5f");
    private static final UUID IMAGE_ID = UUID.fromString("1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d");
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-08-06T10:00:00+02:00");
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
    private static final byte[] BYTES = {1, 2, 3, 4};

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TodoItemImageService service;

    private static TodoItemImage image(String fileName) {
        return new TodoItemImage(
                IMAGE_ID,
                ID,
                "image/png",
                BYTES.length,
                fileName,
                "todos/%s/%s".formatted(ID, IMAGE_ID),
                CREATED,
                false);
    }

    private static MockMultipartFile part(String contentType, String fileName) {
        return new MockMultipartFile("file", fileName, contentType, BYTES);
    }

    @Test
    void uploadReturns201WithLocationAndBody() throws Exception {
        given(service.store(eq(ID), any())).willReturn(image("photo.png"));

        mockMvc.perform(multipart("/api/todos/{id}/images", ID).file(part("image/png", "photo.png")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/todos/" + ID + "/images/" + IMAGE_ID))
                .andExpect(jsonPath("$.id").value(IMAGE_ID.toString()))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(BYTES.length))
                .andExpect(jsonPath("$.fileName").value("photo.png"))
                .andExpect(jsonPath("$.created").exists())
                .andExpect(jsonPath("$.downloadPath").value("/api/todos/" + ID + "/images/" + IMAGE_ID));
    }

    @Test
    void uploadWithoutAFileNameReturnsANullFileName() throws Exception {
        given(service.store(eq(ID), any())).willReturn(image(null));

        mockMvc.perform(multipart("/api/todos/{id}/images", ID).file(part("image/png", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").doesNotExist());
    }

    @Test
    void uploadToAnUnknownItemReturns404() throws Exception {
        given(service.store(eq(ID), any())).willThrow(new TodoItemNotFoundException(ID));

        mockMvc.perform(multipart("/api/todos/{id}/images", ID).file(part("image/png", "photo.png")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void uploadOfANonImageReturns415() throws Exception {
        given(service.store(eq(ID), any())).willThrow(new UnsupportedImageTypeException("application/pdf"));

        mockMvc.perform(multipart("/api/todos/{id}/images", ID).file(part("application/pdf", "invoice.pdf")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void uploadPastTheImageLimitReturns409() throws Exception {
        given(service.store(eq(ID), any()))
                .willThrow(new TooManyImagesException(ID, TodoItemImageService.MAX_IMAGES));

        mockMvc.perform(multipart("/api/todos/{id}/images", ID).file(part("image/png", "photo.png")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void uploadWithoutTheFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/todos/{id}/images", ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void uploadReturns503WhenTheStoreDoesNotAnswer() throws Exception {
        given(service.store(eq(ID), any()))
                .willThrow(new ImageStoreUnavailableException(new IllegalStateException("no route")));

        mockMvc.perform(multipart("/api/todos/{id}/images", ID).file(part("image/png", "photo.png")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void listReturnsTheImagesOfTheItem() throws Exception {
        given(service.findAll(ID)).willReturn(List.of(image("photo.png")));

        mockMvc.perform(get("/api/todos/{id}/images", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(IMAGE_ID.toString()));
    }

    @Test
    void listReturnsAnEmptyArrayWhenTheItemHoldsNoImage() throws Exception {
        given(service.findAll(ID)).willReturn(List.of());

        mockMvc.perform(get("/api/todos/{id}/images", ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listForAnUnknownItemReturns404() throws Exception {
        given(service.findAll(ID)).willThrow(new TodoItemNotFoundException(ID));

        mockMvc.perform(get("/api/todos/{id}/images", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void downloadReturns302WithThePresignedUrlAndNoBody() throws Exception {
        URI presigned = URI.create("http://localhost:3900/todo-images/todos/" + ID + "/" + IMAGE_ID
                + "?X-Amz-Signature=abc");
        given(service.downloadUrl(ID, IMAGE_ID)).willReturn(presigned);

        mockMvc.perform(get("/api/todos/{id}/images/{imageId}", ID, IMAGE_ID))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", presigned.toString()))
                .andExpect(content().string(""));
    }

    @Test
    void downloadOfAnUnknownImageReturns404() throws Exception {
        given(service.downloadUrl(ID, IMAGE_ID)).willThrow(new TodoItemImageNotFoundException(IMAGE_ID));

        mockMvc.perform(get("/api/todos/{id}/images/{imageId}", ID, IMAGE_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/todos/{id}/images/{imageId}", ID, IMAGE_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(service).should().delete(ID, IMAGE_ID);
    }

    @Test
    void deleteOfAnUnknownImageReturns404() throws Exception {
        willThrow(new TodoItemImageNotFoundException(IMAGE_ID)).given(service).delete(ID, IMAGE_ID);

        mockMvc.perform(delete("/api/todos/{id}/images/{imageId}", ID, IMAGE_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }
}
