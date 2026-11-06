package hu.wbstr.todo.item;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
class TodoItemService {

    private final TodoItemRepository repository;
    private final Clock clock;

    TodoItemService(TodoItemRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    TodoItem create(
            String title, String description, OffsetDateTime dueAt, Priority priority, List<String> tags) {
        OffsetDateTime now = now();
        return repository.save(new TodoItem(
                UUID.randomUUID(),
                title,
                description,
                false,
                truncate(dueAt),
                priority != null ? priority : Priority.MEDIUM,
                toTags(tags),
                now,
                now,
                true));
    }

    @Transactional(readOnly = true)
    List<TodoItem> findAll(TodoItemQuery query) {
        return repository.search(query);
    }

    @Transactional(readOnly = true)
    TodoItem findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TodoItemNotFoundException(id));
    }

    TodoItem replace(
            UUID id,
            String title,
            String description,
            boolean done,
            OffsetDateTime dueAt,
            Priority priority,
            List<String> tags) {
        TodoItem existing = findById(id);
        return repository.save(new TodoItem(
                id,
                title,
                description,
                done,
                truncate(dueAt),
                priority != null ? priority : Priority.MEDIUM,
                toTags(tags),
                existing.created(),
                now(),
                false));
    }

    TodoItem patch(
            UUID id,
            String title,
            String description,
            Boolean done,
            OffsetDateTime dueAt,
            Priority priority,
            List<String> tags) {
        TodoItem existing = findById(id);
        return repository.save(new TodoItem(
                id,
                title != null ? title : existing.title(),
                description != null ? description : existing.description(),
                done != null ? done : existing.done(),
                dueAt != null ? truncate(dueAt) : existing.dueAt(),
                priority != null ? priority : existing.priority(),
                tags != null ? toTags(tags) : existing.tags(),
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

    // A Set makes a duplicate tag collapse to one.
    private static Set<TodoItemTag> toTags(List<String> tags) {
        return tags == null
                ? Set.of()
                : tags.stream().map(TodoItemTag::new).collect(Collectors.toUnmodifiableSet());
    }

    // PostgreSQL stores timestamptz with microsecond precision. Truncate so that a
    // response body matches what a later read returns.
    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime truncate(OffsetDateTime value) {
        return value != null ? value.truncatedTo(ChronoUnit.MICROS) : null;
    }
}
