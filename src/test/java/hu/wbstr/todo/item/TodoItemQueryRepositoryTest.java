package hu.wbstr.todo.item;

import hu.wbstr.todo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureDataSourceInitialization
@Import(TestcontainersConfiguration.class)
class TodoItemQueryRepositoryTest {

    private static final OffsetDateTime JANUARY = OffsetDateTime.parse("2026-01-15T10:00:00Z");
    private static final OffsetDateTime FEBRUARY = OffsetDateTime.parse("2026-02-15T10:00:00Z");
    private static final OffsetDateTime MARCH = OffsetDateTime.parse("2026-03-15T10:00:00Z");

    @Autowired
    TodoItemRepository repository;

    @Test
    void returnsEveryItemWithoutAFilter() {
        UUID first = save("Buy milk", false, null, Priority.MEDIUM).id();
        UUID second = save("Buy bread", true, JANUARY, Priority.HIGH).id();

        assertThat(ids(repository.search(filter(null, null, null, null, null))))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void loadsTheTagsOfEveryMatchedItem() {
        UUID id = save("Buy milk", false, null, Priority.MEDIUM, "work", "urgent").id();
        save("Buy bread", false, null, Priority.MEDIUM);

        List<TodoItem> found = repository.search(filter(null, List.of("work"), null, null, null));

        assertThat(found).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.tags())
                    .containsExactlyInAnyOrder(new TodoItemTag("work"), new TodoItemTag("urgent"));
        });
    }

    @Test
    void filtersByOnePriority() {
        UUID high = save("Buy milk", false, null, Priority.HIGH).id();
        save("Buy bread", false, null, Priority.LOW);

        assertThat(ids(repository.search(filter(List.of(Priority.HIGH), null, null, null, null))))
                .containsExactly(high);
    }

    @Test
    void filtersByTwoPriorities() {
        UUID high = save("Buy milk", false, null, Priority.HIGH).id();
        UUID low = save("Buy bread", false, null, Priority.LOW).id();
        save("Buy eggs", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter(List.of(Priority.HIGH, Priority.LOW), null, null, null, null))))
                .containsExactlyInAnyOrder(high, low);
    }

    @Test
    void filtersByOneTag() {
        UUID tagged = save("Buy milk", false, null, Priority.MEDIUM, "work").id();
        save("Buy bread", false, null, Priority.MEDIUM, "home");

        assertThat(ids(repository.search(filter(null, List.of("work"), null, null, null))))
                .containsExactly(tagged);
    }

    @Test
    void filtersByTwoTagsAndReturnsOnlyTheItemsThatHoldBoth() {
        UUID both = save("Buy milk", false, null, Priority.MEDIUM, "work", "urgent").id();
        save("Buy bread", false, null, Priority.MEDIUM, "work");

        assertThat(ids(repository.search(filter(null, List.of("work", "urgent"), null, null, null))))
                .containsExactly(both);
    }

    @Test
    void filtersByTheDoneFlag() {
        UUID open = save("Buy milk", false, null, Priority.MEDIUM).id();
        save("Buy bread", true, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter(null, null, false, null, null)))).containsExactly(open);
    }

    @Test
    void filtersByADueWindow() {
        UUID inside = save("Buy milk", false, FEBRUARY, Priority.MEDIUM).id();
        save("Buy bread", false, MARCH, Priority.MEDIUM);
        save("Buy eggs", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter(null, null, null, MARCH, JANUARY))))
                .containsExactly(inside);
    }

    @Test
    void combinesATagFilterAndTheDoneFlag() {
        UUID match = save("Buy milk", false, null, Priority.MEDIUM, "work").id();
        save("Buy bread", true, null, Priority.MEDIUM, "work");
        save("Buy eggs", false, null, Priority.MEDIUM, "home");

        assertThat(ids(repository.search(filter(null, List.of("work"), false, null, null))))
                .containsExactly(match);
    }

    @Test
    void sortsByTheDueTimestampWithTheNullValuesLastInBothDirections() {
        UUID early = save("Buy milk", false, JANUARY, Priority.MEDIUM).id();
        UUID late = save("Buy bread", false, MARCH, Priority.MEDIUM).id();
        UUID without = save("Buy eggs", false, null, Priority.MEDIUM).id();

        assertThat(ids(repository.search(sort("dueAt", Sort.Direction.ASC))))
                .containsExactly(early, late, without);
        assertThat(ids(repository.search(sort("dueAt", Sort.Direction.DESC))))
                .containsExactly(late, early, without);
    }

    @Test
    void sortsByThePriorityRankInBothDirections() {
        UUID low = save("Buy milk", false, null, Priority.LOW).id();
        UUID medium = save("Buy bread", false, null, Priority.MEDIUM).id();
        UUID high = save("Buy eggs", false, null, Priority.HIGH).id();

        assertThat(ids(repository.search(sort("priority", Sort.Direction.ASC))))
                .containsExactly(low, medium, high);
        assertThat(ids(repository.search(sort("priority", Sort.Direction.DESC))))
                .containsExactly(high, medium, low);
    }

    @Test
    void sortsByTheTitleInBothDirections() {
        UUID bread = save("Buy bread", false, null, Priority.MEDIUM).id();
        UUID milk = save("Buy milk", false, null, Priority.MEDIUM).id();

        assertThat(ids(repository.search(sort("title", Sort.Direction.ASC)))).containsExactly(bread, milk);
        assertThat(ids(repository.search(sort("title", Sort.Direction.DESC)))).containsExactly(milk, bread);
    }

    @Test
    void sortsByTheCreatedTimestampInBothDirections() {
        UUID older = saveAt("Buy milk", JANUARY).id();
        UUID newer = saveAt("Buy bread", MARCH).id();

        assertThat(ids(repository.search(sort("created", Sort.Direction.ASC)))).containsExactly(older, newer);
        assertThat(ids(repository.search(sort("created", Sort.Direction.DESC)))).containsExactly(newer, older);
    }

    @Test
    void sortsByTheUpdatedTimestampInBothDirections() {
        UUID older = saveAt("Buy milk", JANUARY).id();
        UUID newer = saveAt("Buy bread", MARCH).id();

        assertThat(ids(repository.search(sort("updated", Sort.Direction.ASC)))).containsExactly(older, newer);
        assertThat(ids(repository.search(sort("updated", Sort.Direction.DESC)))).containsExactly(newer, older);
    }

    @Test
    void keepsTheOrderOfTwoItemsWithAnEqualSortValueStable() {
        UUID first = saveAt("Buy milk", JANUARY).id();
        UUID second = saveAt("Buy bread", JANUARY).id();

        List<UUID> order = ids(repository.search(sort("created", Sort.Direction.ASC)));

        assertThat(order).containsExactlyInAnyOrder(first, second);
        assertThat(ids(repository.search(sort("created", Sort.Direction.ASC)))).isEqualTo(order);
    }

    private TodoItem save(String title, boolean done, OffsetDateTime dueAt, Priority priority, String... tags) {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        return repository.save(new TodoItem(
                UUID.randomUUID(),
                title,
                null,
                done,
                dueAt,
                priority,
                Arrays.stream(tags).map(TodoItemTag::new).collect(Collectors.toUnmodifiableSet()),
                now,
                now,
                true));
    }

    private TodoItem saveAt(String title, OffsetDateTime created) {
        return repository.save(new TodoItem(
                UUID.randomUUID(),
                title,
                null,
                false,
                null,
                Priority.MEDIUM,
                Set.of(),
                created,
                created,
                true));
    }

    private static TodoItemQuery filter(
            List<Priority> priorities,
            List<String> tags,
            Boolean done,
            OffsetDateTime dueBefore,
            OffsetDateTime dueAfter) {
        return new TodoItemQuery(priorities, tags, done, dueBefore, dueAfter, null, null);
    }

    private static TodoItemQuery sort(String field, Sort.Direction direction) {
        return new TodoItemQuery(null, null, null, null, null, field, direction);
    }

    private static List<UUID> ids(List<TodoItem> items) {
        return items.stream().map(TodoItem::id).toList();
    }
}
