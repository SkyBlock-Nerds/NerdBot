package net.hypixel.nerdbot.discord.util.pagination;

import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatedResponseTest {

    private static final String PREFIX = "suggestions";

    private static PaginatedResponse<Integer> pages(int itemCount, int perPage) {
        List<Integer> items = IntStream.rangeClosed(1, itemCount).boxed().toList();
        return PaginatedResponse.forText(items, perPage, page -> page.toString(), PREFIX);
    }

    private static List<Boolean> disabledStates(PaginatedResponse<?> pagination) {
        ActionRow row = pagination.currentComponents().getFirst();
        return row.getButtons().stream().map(Button::isDisabled).toList();
    }

    @Test
    void splitsItemsIntoPages() {
        PaginatedResponse<Integer> pagination = pages(25, 10);

        assertEquals(3, pagination.getTotalPages());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), pagination.getCurrentPageItems());
    }

    @Test
    void buttonsMoveBetweenPagesAndStopAtTheEdges() {
        PaginatedResponse<Integer> pagination = pages(25, 10);

        assertTrue(pagination.handleAction(PREFIX + ":prev"));
        assertEquals(0, pagination.getCurrentPage());

        pagination.handleAction(PREFIX + ":next");
        assertEquals(List.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20), pagination.getCurrentPageItems());

        pagination.handleAction(PREFIX + ":last");
        assertEquals(List.of(21, 22, 23, 24, 25), pagination.getCurrentPageItems());

        pagination.handleAction(PREFIX + ":next");
        assertEquals(2, pagination.getCurrentPage());

        pagination.handleAction(PREFIX + ":first");
        assertEquals(0, pagination.getCurrentPage());
    }

    @Test
    void anEmptyListIsOneEmptyPageAndLastDoesNotBreakIt() {
        PaginatedResponse<Integer> pagination = pages(0, 10);

        pagination.handleAction(PREFIX + ":last");
        pagination.handleAction(PREFIX + ":prev");

        assertEquals(0, pagination.getCurrentPage());
        assertEquals(List.of(), pagination.getCurrentPageItems());
        assertEquals("[]", pagination.buildCurrentPageResponse().getContent());
    }

    @Test
    void aSinglePageHasNoButtons() {
        assertTrue(pages(10, 10).currentComponents().isEmpty());
        assertTrue(pages(0, 10).currentComponents().isEmpty());
    }

    @Test
    void edgeButtonsAreDisabledOnTheFirstAndLastPage() {
        PaginatedResponse<Integer> pagination = pages(25, 10);

        assertEquals(List.of(true, true, true, false, false), disabledStates(pagination));
        pagination.handleAction(PREFIX + ":next");
        assertEquals(List.of(false, false, true, false, false), disabledStates(pagination));
        pagination.handleAction(PREFIX + ":last");
        assertEquals(List.of(false, false, true, true, true), disabledStates(pagination));
    }

    @Test
    void pageIndicatorShowsThePosition() {
        PaginatedResponse<Integer> pagination = pages(25, 10);
        pagination.handleAction(PREFIX + ":next");

        assertEquals("Page 2/3", pagination.currentComponents().getFirst().getButtons().get(2).getLabel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"other:next", "suggestionsX:next", "suggestions", "suggestions:", "suggestions:page", "suggestions:jump"})
    void buttonsThatAreNotOursAreIgnored(String componentId) {
        PaginatedResponse<Integer> pagination = pages(25, 10);

        assertFalse(pagination.handleAction(componentId));
        assertEquals(0, pagination.getCurrentPage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aPageSizeBelowOneIsRejected(int perPage) {
        assertThrows(IllegalArgumentException.class, () -> pages(5, perPage));
    }

    @Test
    void laterChangesToTheCallersListDoNotChangeThePages() {
        List<Integer> items = new ArrayList<>(List.of(1, 2, 3));
        PaginatedResponse<Integer> pagination = PaginatedResponse.forText(items, 2, page -> page.toString(), PREFIX);

        items.clear();

        assertEquals(List.of(1, 2), pagination.getCurrentPageItems());
        assertThrows(UnsupportedOperationException.class, () -> pagination.getItems().add(4));
    }

    @Test
    void theFirstReplyKeepsBothContentAndEmbeds() {
        MessageEmbed embed = new MessageEmbed(null, "Title", null, EmbedType.RICH, null, 0, null, null, null, null, null, null, List.of());
        PaginatedResponse<Integer> pagination = new PaginatedResponse<>(List.of(1), 5,
            page -> new MessageEditBuilder().setContent("header").setEmbeds(embed).build(), PREFIX);

        MessageCreateData reply = MessageCreateData.fromEditData(pagination.buildCurrentPageResponse());

        assertEquals("header", reply.getContent());
        assertEquals(List.of(embed), reply.getEmbeds());
    }

    @Test
    void concurrentClicksNeverLeaveTheValidRange() throws InterruptedException {
        PaginatedResponse<Integer> pagination = pages(50, 10);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String action = i % 2 == 0 ? ":next" : ":prev";
            threads.add(new Thread(() -> {
                for (int j = 0; j < 1_000; j++) {
                    pagination.handleAction(PREFIX + action);
                    int page = pagination.getCurrentPage();
                    if (page < 0 || page > 4) {
                        throw new AssertionError("page " + page);
                    }
                }
            }));
        }

        List<Throwable> failures = new ArrayList<>();
        threads.forEach(thread -> thread.setUncaughtExceptionHandler((t, e) -> failures.add(e)));
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(List.of(), failures);
    }
}
