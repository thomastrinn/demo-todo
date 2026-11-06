package hu.wbstr.todo.item;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/todos")
class TodoItemController {

    private final TodoItemService service;

    TodoItemController(TodoItemService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TodoItemResponse> create(@Valid @RequestBody CreateTodoItemRequest request) {
        TodoItemWithImages created = service.create(
                request.title(), request.description(), request.dueAt(), request.priority(), request.tags());
        return ResponseEntity.created(URI.create("/api/todos/" + created.item().id()))
                .body(TodoItemResponse.of(created));
    }

    @GetMapping
    TodoItemPageResponse findAll(@Valid TodoItemListParams params) {
        TodoItemQuery query = toQuery(params);
        return TodoItemPageResponse.of(service.findAll(query), query.limit(), query.offset());
    }

    @GetMapping("/{id}")
    TodoItemResponse findById(@PathVariable UUID id) {
        return TodoItemResponse.of(service.findById(id));
    }

    @PutMapping("/{id}")
    TodoItemResponse replace(@PathVariable UUID id, @Valid @RequestBody ReplaceTodoItemRequest request) {
        return TodoItemResponse.of(service.replace(
                id,
                request.title(),
                request.description(),
                request.done(),
                request.dueAt(),
                request.priority(),
                request.tags()));
    }

    @PatchMapping("/{id}")
    TodoItemResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchTodoItemRequest request) {
        return TodoItemResponse.of(service.patch(
                id,
                request.title(),
                request.description(),
                request.done(),
                request.dueAt(),
                request.priority(),
                request.tags()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    private static TodoItemQuery toQuery(TodoItemListParams params) {
        String searchText = searchText(params);
        // A search orders by the relevance, every other request by the created timestamp.
        String field = searchText != null ? TodoItemQuery.RELEVANCE_SORT_FIELD : TodoItemQuery.DEFAULT_SORT_FIELD;
        Sort.Direction direction = Sort.Direction.DESC;
        if (params.sort() != null) {
            String[] parts = params.sort().split(",", 2);
            field = parts[0];
            direction = parts.length == 2
                    ? Sort.Direction.fromOptionalString(parts[1])
                            .orElseThrow(() -> new InvalidTodoItemSortException(params.sort()))
                    : Sort.Direction.ASC;
            if (!TodoItemQuery.SORT_FIELDS.contains(field)) {
                throw new InvalidTodoItemSortException(params.sort());
            }
            // The relevance expression binds the search text, so it needs one.
            if (TodoItemQuery.RELEVANCE_SORT_FIELD.equals(field) && searchText == null) {
                throw new InvalidTodoItemSortException(params.sort());
            }
        }
        return new TodoItemQuery(
                searchText,
                params.priority(),
                params.tag(),
                params.done(),
                params.dueBefore(),
                params.dueAfter(),
                field,
                direction,
                limit(params),
                offset(params));
    }

    private static String searchText(TodoItemListParams params) {
        if (params.q() == null || params.q().isBlank()) {
            return null;
        }
        return params.q().trim();
    }

    private static int limit(TodoItemListParams params) {
        return params.limit() != null ? params.limit() : TodoItemQuery.DEFAULT_LIMIT;
    }

    private static int offset(TodoItemListParams params) {
        return params.offset() != null ? params.offset() : TodoItemQuery.DEFAULT_OFFSET;
    }
}
