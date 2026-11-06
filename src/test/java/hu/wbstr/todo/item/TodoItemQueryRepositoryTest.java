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

        assertThat(ids(repository.search(filter(null, null, null, null, null, null))))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void loadsTheTagsOfEveryMatchedItem() {
        UUID id = save("Buy milk", false, null, Priority.MEDIUM, "work", "urgent").id();
        save("Buy bread", false, null, Priority.MEDIUM);

        TodoItemPage<TodoItem> found = repository.search(filter(null, null, List.of("work"), null, null, null));

        assertThat(found.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.tags())
                    .containsExactlyInAnyOrder(new TodoItemTag("work"), new TodoItemTag("urgent"));
        });
    }

    @Test
    void filtersByOnePriority() {
        UUID high = save("Buy milk", false, null, Priority.HIGH).id();
        save("Buy bread", false, null, Priority.LOW);

        assertThat(ids(repository.search(filter(null, List.of(Priority.HIGH), null, null, null, null))))
                .containsExactly(high);
    }

    @Test
    void filtersByTwoPriorities() {
        UUID high = save("Buy milk", false, null, Priority.HIGH).id();
        UUID low = save("Buy bread", false, null, Priority.LOW).id();
        save("Buy eggs", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter(null, List.of(Priority.HIGH, Priority.LOW), null, null, null, null))))
                .containsExactlyInAnyOrder(high, low);
    }

    @Test
    void filtersByOneTag() {
        UUID tagged = save("Buy milk", false, null, Priority.MEDIUM, "work").id();
        save("Buy bread", false, null, Priority.MEDIUM, "home");

        assertThat(ids(repository.search(filter(null, null, List.of("work"), null, null, null))))
                .containsExactly(tagged);
    }

    @Test
    void filtersByTwoTagsAndReturnsOnlyTheItemsThatHoldBoth() {
        UUID both = save("Buy milk", false, null, Priority.MEDIUM, "work", "urgent").id();
        save("Buy bread", false, null, Priority.MEDIUM, "work");

        assertThat(ids(repository.search(filter(null, null, List.of("work", "urgent"), null, null, null))))
                .containsExactly(both);
    }

    @Test
    void filtersByTheDoneFlag() {
        UUID open = save("Buy milk", false, null, Priority.MEDIUM).id();
        save("Buy bread", true, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter(null, null, null, false, null, null)))).containsExactly(open);
    }

    @Test
    void filtersByADueWindow() {
        UUID inside = save("Buy milk", false, FEBRUARY, Priority.MEDIUM).id();
        save("Buy bread", false, MARCH, Priority.MEDIUM);
        save("Buy eggs", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter(null, null, null, null, MARCH, JANUARY))))
                .containsExactly(inside);
    }

    @Test
    void combinesATagFilterAndTheDoneFlag() {
        UUID match = save("Buy milk", false, null, Priority.MEDIUM, "work").id();
        save("Buy bread", true, null, Priority.MEDIUM, "work");
        save("Buy eggs", false, null, Priority.MEDIUM, "home");

        assertThat(ids(repository.search(filter(null, null, List.of("work"), false, null, null))))
                .containsExactly(match);
    }

    @Test
    void matchesAWordInTheTitle() {
        UUID match = save("Pay the invoice", false, null, Priority.MEDIUM).id();
        save("Buy milk", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(search("invoice")))).containsExactly(match);
    }

    @Test
    void matchesAWordInTheDescription() {
        UUID match = save("Buy milk", "Pay the invoice", false, null, Priority.MEDIUM).id();
        save("Buy bread", "Two loaves", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(search("invoice")))).containsExactly(match);
    }

    @Test
    void ignoresTheCaseOfTheSearchText() {
        UUID match = save("Pay the invoice", false, null, Priority.MEDIUM).id();

        assertThat(ids(repository.search(search("INVOICE")))).containsExactly(match);
    }

    @Test
    void matchesAWordInsideALongerTitle() {
        UUID match = save("Pay the invoice today", false, null, Priority.MEDIUM).id();

        assertThat(ids(repository.search(search("invoice")))).containsExactly(match);
    }

    @Test
    void matchesOnlyTheItemsThatHoldBothWords() {
        UUID both = save("Pay the office invoice", false, null, Priority.MEDIUM).id();
        save("Pay the invoice", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(search("invoice office")))).containsExactly(both);
    }

    @Test
    void matchesAQuotedPhraseOnlyInThatWordOrder() {
        UUID inOrder = save("Pay the invoice", false, null, Priority.MEDIUM).id();
        save("Invoice the pay", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(search("\"pay the invoice\"")))).containsExactly(inOrder);
    }

    @Test
    void matchesEitherWordAroundAnOr() {
        UUID invoice = save("Pay the invoice", false, null, Priority.MEDIUM).id();
        UUID receipt = save("File the receipt", false, null, Priority.MEDIUM).id();
        save("Buy milk", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(search("invoice or receipt"))))
                .containsExactlyInAnyOrder(invoice, receipt);
    }

    @Test
    void dropsTheItemsThatHoldAWordWithALeadingMinus() {
        UUID without = save("Pay the invoice", false, null, Priority.MEDIUM).id();
        save("Pay the office invoice", false, null, Priority.MEDIUM);

        assertThat(ids(repository.search(search("invoice -office")))).containsExactly(without);
    }

    @Test
    void returnsNoItemWhenTheSearchTextHoldsNoSearchableWord() {
        save("Pay the invoice", false, null, Priority.MEDIUM);

        assertThat(repository.search(search("+++")).items()).isEmpty();
    }

    @Test
    void doesNotMatchATagValue() {
        save("Buy milk", false, null, Priority.MEDIUM, "work");

        assertThat(repository.search(search("work")).items()).isEmpty();
    }

    @Test
    void combinesTheSearchAndThePriorityFilter() {
        UUID high = save("Pay the invoice", false, null, Priority.HIGH).id();
        save("Pay the invoice twice", false, null, Priority.LOW);
        save("Buy milk", false, null, Priority.HIGH);

        assertThat(ids(repository.search(filter("invoice", List.of(Priority.HIGH), null, null, null, null))))
                .containsExactly(high);
    }

    @Test
    void combinesTheSearchAndTheDoneFlag() {
        UUID open = save("Pay the invoice", false, null, Priority.MEDIUM).id();
        save("Pay the invoice again", true, null, Priority.MEDIUM);

        assertThat(ids(repository.search(filter("invoice", null, null, false, null, null))))
                .containsExactly(open);
    }

    @Test
    void combinesTheSearchWithATagAndADueWindow() {
        UUID match = save("Pay the invoice", false, FEBRUARY, Priority.MEDIUM, "work").id();
        save("Pay the invoice late", false, MARCH, Priority.MEDIUM, "work");
        save("Pay the invoice", false, FEBRUARY, Priority.MEDIUM, "home");
        save("Buy milk", false, FEBRUARY, Priority.MEDIUM, "work");

        assertThat(ids(repository.search(filter("invoice", null, List.of("work"), null, MARCH, null))))
                .containsExactly(match);
    }

    @Test
    void putsATitleHitBeforeADescriptionHitInARelevanceSort() {
        UUID titleHit = save("Pay the invoice", "Nothing else", false, null, Priority.MEDIUM).id();
        UUID descriptionHit = save("Buy milk", "Pay the invoice", false, null, Priority.MEDIUM).id();

        assertThat(ids(repository.search(search("invoice", "relevance", Sort.Direction.DESC))))
                .containsExactly(titleHit, descriptionHit);
    }

    @Test
    void keepsTheSearchFilterUnderAnExplicitSort() {
        UUID early = save("Pay the invoice", false, JANUARY, Priority.MEDIUM).id();
        UUID late = save("Pay the invoice again", false, MARCH, Priority.MEDIUM).id();
        save("Buy milk", false, FEBRUARY, Priority.MEDIUM);

        assertThat(ids(repository.search(search("invoice", "dueAt", Sort.Direction.ASC))))
                .containsExactly(early, late);
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

    @Test
    void returnsTheFirstItemsOfTheOrderUpToTheLimit() {
        List<UUID> order = saveFiveInCreatedOrder();

        TodoItemPage<TodoItem> found = repository.search(page(2, 0));

        assertThat(ids(found)).containsExactly(order.get(0), order.get(1));
        assertThat(found.total()).isEqualTo(5);
    }

    @Test
    void skipsTheEarlierMatchesOfTheOffset() {
        List<UUID> order = saveFiveInCreatedOrder();

        TodoItemPage<TodoItem> found = repository.search(page(2, 2));

        assertThat(ids(found)).containsExactly(order.get(2), order.get(3));
        assertThat(found.total()).isEqualTo(5);
    }

    @Test
    void returnsTheRestOfTheMatchesOnTheLastPage() {
        List<UUID> order = saveFiveInCreatedOrder();

        TodoItemPage<TodoItem> found = repository.search(page(2, 4));

        assertThat(ids(found)).containsExactly(order.get(4));
        assertThat(found.total()).isEqualTo(5);
    }

    @Test
    void returnsAnEmptyPageWithTheTotalWhenTheOffsetIsPastTheLastMatch() {
        saveFiveInCreatedOrder();

        TodoItemPage<TodoItem> found = repository.search(page(20, 500));

        assertThat(found.items()).isEmpty();
        assertThat(found.total()).isEqualTo(5);
    }

    @Test
    void countsTheMatchesOfTheFilterAndNotTheStoredItems() {
        save("Buy milk", false, null, Priority.MEDIUM);
        save("Buy bread", false, null, Priority.MEDIUM);
        save("Buy eggs", true, null, Priority.MEDIUM);

        TodoItemPage<TodoItem> found =
                repository.search(new TodoItemQuery(null, null, null, false, null, null, null, null, 1, 0));

        assertThat(found.items()).hasSize(1);
        assertThat(found.total()).isEqualTo(2);
    }

    @Test
    void keepsTheRequestedSortUnderThePaging() {
        save("Buy apples", false, null, Priority.MEDIUM);
        save("Buy bread", false, null, Priority.MEDIUM);
        UUID eggs = save("Buy eggs", false, null, Priority.MEDIUM).id();
        UUID milk = save("Buy milk", false, null, Priority.MEDIUM).id();
        save("Buy nuts", false, null, Priority.MEDIUM);

        TodoItemPage<TodoItem> found = repository.search(page(null, "title", Sort.Direction.ASC, 2, 2));

        assertThat(ids(found)).containsExactly(eggs, milk);
        assertThat(found.total()).isEqualTo(5);
    }

    @Test
    void keepsTheSearchFilterAndTheRelevanceOrderUnderThePaging() {
        UUID titleHit = save("Pay the invoice", "Nothing else", false, null, Priority.MEDIUM).id();
        save("Buy milk", "Pay the invoice", false, null, Priority.MEDIUM);
        save("Buy bread", false, null, Priority.MEDIUM);

        TodoItemPage<TodoItem> found = repository.search(page("invoice", "relevance", Sort.Direction.DESC, 1, 0));

        assertThat(ids(found)).containsExactly(titleHit);
        assertThat(found.total()).isEqualTo(2);
    }

    private List<UUID> saveFiveInCreatedOrder() {
        return List.of(
                saveAt("Buy milk", JANUARY).id(),
                saveAt("Buy bread", JANUARY.plusDays(1)).id(),
                saveAt("Buy eggs", FEBRUARY).id(),
                saveAt("Buy nuts", FEBRUARY.plusDays(1)).id(),
                saveAt("Buy tea", MARCH).id());
    }

    private TodoItem save(String title, boolean done, OffsetDateTime dueAt, Priority priority, String... tags) {
        return save(title, null, done, dueAt, priority, tags);
    }

    private TodoItem save(
            String title,
            String description,
            boolean done,
            OffsetDateTime dueAt,
            Priority priority,
            String... tags) {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        return repository.save(new TodoItem(
                UUID.randomUUID(),
                title,
                description,
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
            String searchText,
            List<Priority> priorities,
            List<String> tags,
            Boolean done,
            OffsetDateTime dueBefore,
            OffsetDateTime dueAfter) {
        return new TodoItemQuery(
                searchText,
                priorities,
                tags,
                done,
                dueBefore,
                dueAfter,
                null,
                null,
                TodoItemQuery.MAX_LIMIT,
                TodoItemQuery.DEFAULT_OFFSET);
    }

    private static TodoItemQuery search(String searchText) {
        return filter(searchText, null, null, null, null, null);
    }

    private static TodoItemQuery search(String searchText, String field, Sort.Direction direction) {
        return page(searchText, field, direction, TodoItemQuery.MAX_LIMIT, TodoItemQuery.DEFAULT_OFFSET);
    }

    private static TodoItemQuery sort(String field, Sort.Direction direction) {
        return page(null, field, direction, TodoItemQuery.MAX_LIMIT, TodoItemQuery.DEFAULT_OFFSET);
    }

    private static TodoItemQuery page(int limit, int offset) {
        return page(null, null, null, limit, offset);
    }

    private static TodoItemQuery page(
            String searchText, String field, Sort.Direction direction, int limit, int offset) {
        return new TodoItemQuery(searchText, null, null, null, null, null, field, direction, limit, offset);
    }

    private static List<UUID> ids(TodoItemPage<TodoItem> page) {
        return page.items().stream().map(TodoItem::id).toList();
    }
}
