package hu.wbstr.todo.item;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
class TodoItemImageService {

    static final int MAX_IMAGES = 20;

    private final TodoItemRepository itemRepository;
    private final TodoItemImageRepository repository;
    private final TodoItemImageStore store;
    private final Clock clock;

    TodoItemImageService(
            TodoItemRepository itemRepository,
            TodoItemImageRepository repository,
            TodoItemImageStore store,
            Clock clock) {
        this.itemRepository = itemRepository;
        this.repository = repository;
        this.store = store;
        this.clock = clock;
    }

    TodoItemImage store(UUID itemId, MultipartFile file) {
        requireItem(itemId);
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new UnsupportedImageTypeException(contentType);
        }
        if (repository.countByTodoItemId(itemId) >= MAX_IMAGES) {
            throw new TooManyImagesException(itemId, MAX_IMAGES);
        }

        UUID id = UUID.randomUUID();
        String storageKey = "todos/%s/%s".formatted(itemId, id);
        try (InputStream stream = file.getInputStream()) {
            store.put(storageKey, contentType, file.getSize(), stream);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        // The object exists now. A failed row leaves it orphaned, so remove it.
        try {
            return repository.save(new TodoItemImage(
                    id,
                    itemId,
                    contentType,
                    file.getSize(),
                    fileName(file),
                    storageKey,
                    now(),
                    true));
        } catch (RuntimeException exception) {
            store.delete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    List<TodoItemImage> findAll(UUID itemId) {
        requireItem(itemId);
        return repository.findByTodoItemIdOrderByCreatedAscIdAsc(itemId);
    }

    @Transactional(readOnly = true)
    TodoItemImage findOne(UUID itemId, UUID imageId) {
        return repository.findByIdAndTodoItemId(imageId, itemId)
                .orElseThrow(() -> new TodoItemImageNotFoundException(imageId));
    }

    @Transactional(readOnly = true)
    URI downloadUrl(UUID itemId, UUID imageId) {
        TodoItemImage image = findOne(itemId, imageId);
        return store.presignGet(image.storageKey(), image.contentType(), image.fileName());
    }

    void delete(UUID itemId, UUID imageId) {
        TodoItemImage image = findOne(itemId, imageId);
        repository.deleteById(image.id());
        store.delete(image.storageKey());
    }

    private void requireItem(UUID itemId) {
        if (!itemRepository.existsById(itemId)) {
            throw new TodoItemNotFoundException(itemId);
        }
    }

    // A part without a file name arrives as an empty string.
    private static String fileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return fileName != null && !fileName.isBlank() ? fileName : null;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
