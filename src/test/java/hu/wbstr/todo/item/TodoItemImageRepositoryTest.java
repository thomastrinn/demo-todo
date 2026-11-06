package hu.wbstr.todo.item;

import hu.wbstr.todo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureDataSourceInitialization
@Import(TestcontainersConfiguration.class)
class TodoItemImageRepositoryTest {

    @Autowired
    TodoItemRepository itemRepository;

    @Autowired
    TodoItemImageRepository repository;

    private static OffsetDateTime now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private TodoItem givenItem() {
        OffsetDateTime now = now();
        return itemRepository.save(new TodoItem(
                UUID.randomUUID(), "Buy milk", null, false, null, Priority.MEDIUM, Set.of(), now, now, true));
    }

    private TodoItemImage image(UUID itemId, String fileName, OffsetDateTime created) {
        UUID id = UUID.randomUUID();
        return new TodoItemImage(
                id,
                itemId,
                "image/png",
                12L,
                fileName,
                "todos/%s/%s".formatted(itemId, id),
                created,
                true);
    }

    @Test
    void savesAndReadsBackAnImage() {
        TodoItem item = givenItem();
        OffsetDateTime created = now();
        TodoItemImage image = image(item.id(), "photo.png", created);

        repository.save(image);

        TodoItemImage found = repository.findById(image.id()).orElseThrow();
        assertThat(found.todoItemId()).isEqualTo(item.id());
        assertThat(found.contentType()).isEqualTo("image/png");
        assertThat(found.sizeBytes()).isEqualTo(12L);
        assertThat(found.fileName()).isEqualTo("photo.png");
        assertThat(found.storageKey()).isEqualTo(image.storageKey());
        assertThat(found.created().toInstant()).isEqualTo(created.toInstant());
    }

    @Test
    void savesAnImageWithoutAFileName() {
        TodoItem item = givenItem();
        TodoItemImage image = image(item.id(), null, now());

        repository.save(image);

        assertThat(repository.findById(image.id()).orElseThrow().fileName()).isNull();
    }

    @Test
    void readsTheImagesOfOneItemOldestFirst() {
        TodoItem item = givenItem();
        TodoItem other = givenItem();
        OffsetDateTime now = now();
        TodoItemImage older = image(item.id(), "older.png", now.minusMinutes(1));
        TodoItemImage newer = image(item.id(), "newer.png", now);
        repository.save(newer);
        repository.save(older);
        repository.save(image(other.id(), "other.png", now));

        assertThat(repository.findByTodoItemIdOrderByCreatedAscIdAsc(item.id()))
                .extracting(TodoItemImage::id)
                .containsExactly(older.id(), newer.id());
    }

    @Test
    void readsTheImagesOfSeveralItemsInOneQuery() {
        TodoItem first = givenItem();
        TodoItem second = givenItem();
        TodoItemImage firstImage = image(first.id(), "first.png", now());
        TodoItemImage secondImage = image(second.id(), "second.png", now());
        repository.save(firstImage);
        repository.save(secondImage);

        assertThat(repository.findByTodoItemIdInOrderByCreatedAscIdAsc(Set.of(first.id(), second.id())))
                .extracting(TodoItemImage::id)
                .containsExactlyInAnyOrder(firstImage.id(), secondImage.id());
    }

    @Test
    void readsAnImageByIdAndItem() {
        TodoItem item = givenItem();
        TodoItem other = givenItem();
        TodoItemImage image = image(item.id(), "photo.png", now());
        repository.save(image);

        assertThat(repository.findByIdAndTodoItemId(image.id(), item.id())).isPresent();
        assertThat(repository.findByIdAndTodoItemId(image.id(), other.id())).isEmpty();
    }

    @Test
    void countsTheImagesOfOneItem() {
        TodoItem item = givenItem();
        repository.save(image(item.id(), "one.png", now()));
        repository.save(image(item.id(), "two.png", now()));

        assertThat(repository.countByTodoItemId(item.id())).isEqualTo(2);
    }

    @Test
    void deletingTheItemRemovesItsImageRows() {
        TodoItem item = givenItem();
        TodoItemImage image = image(item.id(), "photo.png", now());
        repository.save(image);

        itemRepository.deleteById(item.id());

        assertThat(repository.findById(image.id())).isEmpty();
    }
}
