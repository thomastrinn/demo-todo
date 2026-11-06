package hu.wbstr.todo.item;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
class TodoItemService {

    private final TodoItemRepository repository;
    private final Clock clock;

    TodoItemService(TodoItemRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    TodoItem create(String title, String description) {
        OffsetDateTime now = now();
        return repository.save(new TodoItem(UUID.randomUUID(), title, description, false, now, now, true));
    }

    @Transactional(readOnly = true)
    List<TodoItem> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    TodoItem findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TodoItemNotFoundException(id));
    }

    TodoItem replace(UUID id, String title, String description, boolean done) {
        TodoItem existing = findById(id);
        return repository.save(new TodoItem(id, title, description, done, existing.created(), now(), false));
    }

    TodoItem patch(UUID id, String title, String description, Boolean done) {
        TodoItem existing = findById(id);
        return repository.save(new TodoItem(
                id,
                title != null ? title : existing.title(),
                description != null ? description : existing.description(),
                done != null ? done : existing.done(),
                existing.created(),
                now(),
                false));
    }

    void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new TodoItemNotFoundException(id);
        }
        repository.deleteById(id);
    }

    // PostgreSQL stores timestamptz with microsecond precision. Truncate so that a
    // response body matches what a later read returns.
    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
