package hu.wbstr.todo.item;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/todos/{id}/images")
class TodoItemImageController {

    private final TodoItemImageService service;

    TodoItemImageController(TodoItemImageService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TodoItemImageResponse> upload(
            @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        TodoItemImageResponse response = TodoItemImageResponse.of(service.store(id, file));
        return ResponseEntity.created(URI.create(response.downloadPath())).body(response);
    }

    @GetMapping
    List<TodoItemImageResponse> findAll(@PathVariable UUID id) {
        return service.findAll(id).stream().map(TodoItemImageResponse::of).toList();
    }

    // The bytes stay in the object store. The client reads them from the presigned URL.
    @GetMapping("/{imageId}")
    ResponseEntity<Void> download(@PathVariable UUID id, @PathVariable UUID imageId) {
        return ResponseEntity.status(HttpStatus.FOUND).location(service.downloadUrl(id, imageId)).build();
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @PathVariable UUID imageId) {
        service.delete(id, imageId);
    }
}
