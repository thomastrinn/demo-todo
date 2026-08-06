package hu.wbstr.todo.item;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
class TodoItemService {

    private final TodoItemRepository repository;
    private final TodoItemImageRepository imageRepository;
    private final TodoItemImageStore imageStore;
    private final Clock clock;

    TodoItemService(
            TodoItemRepository repository,
            TodoItemImageRepository imageRepository,
            TodoItemImageStore imageStore,
            Clock clock) {
        this.repository = repository;
        this.imageRepository = imageRepository;
        this.imageStore = imageStore;
        this.clock = clock;
    }

    TodoItemWithImages create(
            String title, String description, OffsetDateTime dueAt, Priority priority, List<String> tags) {
        OffsetDateTime now = now();
        TodoItem created = repository.save(new TodoItem(
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
        return new TodoItemWithImages(created, List.of());
    }

    @Transactional(readOnly = true)
    List<TodoItemWithImages> findAll(TodoItemQuery query) {
        List<TodoItem> items = repository.search(query);
        if (items.isEmpty()) {
            return List.of();
        }
        // One query for the whole page, grouped in memory, instead of a query per item.
        Map<UUID, List<TodoItemImage>> images = imageRepository
                .findByTodoItemIdInOrderByCreatedAscIdAsc(items.stream().map(TodoItem::id).toList())
                .stream()
                .collect(Collectors.groupingBy(TodoItemImage::todoItemId));
        return items.stream()
                .map(item -> new TodoItemWithImages(item, images.getOrDefault(item.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    TodoItemWithImages findById(UUID id) {
        return withImages(find(id));
    }

    TodoItemWithImages replace(
            UUID id,
            String title,
            String description,
            boolean done,
            OffsetDateTime dueAt,
            Priority priority,
            List<String> tags) {
        TodoItem existing = find(id);
        return withImages(repository.save(new TodoItem(
                id,
                title,
                description,
                done,
                truncate(dueAt),
                priority != null ? priority : Priority.MEDIUM,
                toTags(tags),
                existing.created(),
                now(),
                false)));
    }

    // A present field takes its value, including a null that clears the field. An
    // undefined field keeps the value of the stored item. Validation rejects a present
    // null on a field that may not be null before the request reaches this method.
    TodoItemWithImages patch(
            UUID id,
            Patchable<String> title,
            Patchable<String> description,
            Patchable<Boolean> done,
            Patchable<OffsetDateTime> dueAt,
            Patchable<Priority> priority,
            Patchable<List<String>> tags) {
        TodoItem existing = find(id);
        return withImages(repository.save(new TodoItem(
                id,
                title.isPresent() ? title.get() : existing.title(),
                description.isPresent() ? description.get() : existing.description(),
                done.isPresent() ? done.get() : existing.done(),
                dueAt.isPresent() ? truncate(dueAt.get()) : existing.dueAt(),
                priority.isPresent() ? priority.get() : existing.priority(),
                tags.isPresent() ? toTags(tags.get()) : existing.tags(),
                existing.created(),
                now(),
                false)));
    }

    void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new TodoItemNotFoundException(id);
        }
        // The foreign key cascade removes the rows, so read the keys before the delete.
        List<String> storageKeys = imageRepository.findByTodoItemIdOrderByCreatedAscIdAsc(id).stream()
                .map(TodoItemImage::storageKey)
                .toList();
        repository.deleteById(id);
        storageKeys.forEach(imageStore::delete);
    }

    private TodoItem find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TodoItemNotFoundException(id));
    }

    private TodoItemWithImages withImages(TodoItem item) {
        return new TodoItemWithImages(item, imageRepository.findByTodoItemIdOrderByCreatedAscIdAsc(item.id()));
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
