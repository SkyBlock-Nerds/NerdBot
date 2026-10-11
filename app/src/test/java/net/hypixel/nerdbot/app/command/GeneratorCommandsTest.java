package net.hypixel.nerdbot.app.command;

import gnu.trove.map.hash.TLongObjectHashMap;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.tools.SearchResult;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.generation.DiscordGenerationContexts;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorCommandsTest {

    private static final String MEMBER = "100";
    private static final String GEN_CHANNEL = "300";
    private static final String COMMAND = "/gen text";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final ResourcePackService packs = new ResourcePackService(new PackRepository());
    private Executor rendering = Runnable::run;
    private final GeneratorCommands commands = new GeneratorCommands(packs, () -> config, users,
        new DiscordGenerationContexts(() -> config, Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)),
        task -> rendering.execute(task));

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void textIsRenderedAsAPngAndRecorded() {
        DiscordUser member = member(false);

        commands.generateText(event(GEN_CHANNEL), "&ahello", null, null, null, null, null, null, null, null);

        assertFalse(deferredHidden());
        FileUpload upload = onlyFile(lastEdit());
        assertEquals("text.png", upload.getName());
        assertTrue(startsWithPng(upload));
        assertEquals(List.of(COMMAND), member.getGeneratorHistory().getCommandHistory());
        assertEquals(List.of(MEMBER), users.cachedIds());
    }

    @Test
    void theMembersPreferenceHidesTheReply() {
        member(true);

        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);

        assertTrue(deferredHidden());
    }

    @Test
    void anExplicitChoiceBeatsThePreference() {
        member(true);

        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, false);

        assertFalse(deferredHidden());
    }

    @Test
    void withoutARecordTheImageIsStillMadeButHidden() {
        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);

        assertTrue(deferredHidden());
        assertEquals("text.png", onlyFile(lastEdit()).getName());
        assertEquals(List.of(), users.cachedIds());
    }

    @Test
    void aFailedLookupStillMakesTheImageHidden() {
        member(false);
        users.throwOnFindFor(MEMBER);

        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);

        assertTrue(deferredHidden());
        assertEquals("text.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void outsideTheGeneratorChannelsNothingRuns() {
        config.getChannelConfig().setGenChannelIds(new String[]{GEN_CHANNEL});

        commands.generateText(event("999"), "hello", null, null, null, null, null, null, null, null);

        assertEquals(GeneratorCommands.WRONG_CHANNEL_MESSAGE, recorder.last("setContent").arg(0));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
        assertFalse(recorder.called("editOriginal"));
        assertEquals(List.of(), users.cachedIds());
    }

    @Test
    void insideAConfiguredGeneratorChannelItRuns() {
        config.getChannelConfig().setGenChannelIds(new String[]{GEN_CHANNEL});

        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);

        assertEquals("text.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void renderingWaitsForTheRenderingPool() {
        List<Runnable> queued = new ArrayList<>();
        rendering = queued::add;

        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);

        assertTrue(recorder.called("deferReply"));
        assertFalse(recorder.called("editOriginal"));
        assertEquals(1, queued.size());

        queued.getFirst().run();
        assertEquals("text.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void inputTheGeneratorRejectsIsShownAndNotRecorded() {
        DiscordUser member = member(false);

        commands.generateInventory(event(GEN_CHANNEL), 0, 9, "stone", null, null, null, null, null, null);

        assertEquals("rows must be positive", DiscordFakes.lastEditText(recorder));
        assertEquals(List.of(), member.getGeneratorHistory().getCommandHistory());
    }

    @Test
    void anUnknownPackIsShown() {
        commands.generateRecipe(event(GEN_CHANNEL), "stone:1", null, "nope:nope", null);

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("Pack `nope:nope` is not loaded!"));
    }

    @Test
    void anUnexpectedFailureGetsTheSafeMessage() {
        render(context -> {
            throw new IOException("disk full at /tmp/secret");
        });

        assertEquals("Couldn't render", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aRuntimeFailureGetsTheSafeMessage() {
        render(context -> {
            throw new IllegalStateException("bug");
        });

        assertEquals("Couldn't render", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aRejectionWithoutAMessageGetsTheSafeMessage() {
        render(context -> {
            throw new GeneratorException(" ");
        });

        assertEquals("Couldn't render", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aLongRejectionIsCutToDiscordsLimit() {
        render(context -> {
            throw new IllegalArgumentException("x".repeat(2500));
        });

        assertEquals(2000, DiscordFakes.lastEditText(recorder).length());
    }

    @Test
    void anEditDiscordRefusesIsReported() {
        List<Object> edits = new ArrayList<>();
        WebhookMessageEditAction<?> refused = Fakes.of(WebhookMessageEditAction.class, Map.of("queue", (CallRecorder.Answer) args -> {
            if (args.length == 2) {
                consumer(args[1]).accept(new IllegalStateException("Request entity too large"));
            }
            return null;
        }));
        InteractionHook hook = Fakes.of(InteractionHook.class, Map.of("editOriginal", (CallRecorder.Answer) args -> {
            edits.add(args[0]);
            return refused;
        }));

        commands.answer(interactionWithHook(GEN_CHANNEL, hook), null, "Couldn't render", context -> MessageEditData.fromContent("big"), user -> {
        }).join();

        assertEquals("Couldn't render", edits.getLast());
    }

    @Test
    void searchListsResultsWithoutRecording() {
        DiscordUser member = member(false);

        commands.searchItem(event(GEN_CHANNEL), "diamond", null);

        assertTrue(lastEdit().getContent().startsWith("Top results for `diamond`"));
        assertEquals(List.of(), member.getGeneratorHistory().getCommandHistory());
    }

    @Test
    void searchWithNoResultsSaysSo() {
        assertEquals("No results found for that item!", GeneratorCommands.searchReply("zzz", new SearchResult(List.of(), List.of())));
    }

    @Test
    void symbolsWithNoMatchSayWhichFilter() {
        commands.listSymbols(event(GEN_CHANNEL), "no-such-symbol-anywhere", null);

        assertEquals("No placeholders match `no-such-symbol-anywhere`!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void symbolsArePagedIntoTheDeferredReply() {
        commands.listSymbols(event(GEN_CHANNEL), null, null);

        assertTrue(recorder.called("editOriginal"));
        assertFalse(DiscordFakes.lastComponents(recorder).isEmpty());
        assertTrue(recorder.called("retrieveOriginal"));
    }

    @Test
    void parsedNbtIsReturnedAsACommandAndImage() {
        commands.parseNbtString(event(GEN_CHANNEL), "{id:\"minecraft:diamond_sword\",Count:1b,tag:{display:{Name:'{\"text\":\"Hi\"}'}}}",
            null, null, null);

        MessageEditData reply = lastEdit();
        assertTrue(reply.getContent().startsWith("Your NBT text input has been parsed into a slash command:\n```\n/gen item item_name: Hi"));
        assertEquals("parsed_nbt.png", onlyFile(reply).getName());
    }

    @Test
    void nbtThatWontParseIsShown() {
        commands.parseNbtString(event(GEN_CHANNEL), "{{{", null, null, null);

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("Input is neither valid JSON nor valid SNBT"));
    }

    @Test
    void parseWithNothingToParseAsksForInput() {
        commands.parseNbtString(event(GEN_CHANNEL), "  ", null, null, null);

        assertEquals("Please provide NBT data either as text or by uploading a file.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aPowerStoneCanIncludeItsSlashCommand() {
        commands.generatePowerstone(event(GEN_CHANNEL), "Test", "Starter", 100, "strength:1", null, null, null, null, null, null,
            true, null, null, null, null);

        assertTrue(((String) recorder.last("sendMessage").arg(0)).startsWith("Your Power Stone has been parsed into a slash command:\n```/gen item"));
        assertEquals("powerstone.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void anItemIsDisplayed() {
        commands.generateItem(event(GEN_CHANNEL), "diamond_sword", null, null, null, null, null, null, null, null, null);

        assertEquals("item.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void aRecipeIsRendered() {
        commands.generateRecipe(event(GEN_CHANNEL), "stone:1%%stone:5%%stone:9", null, null, null);

        assertEquals("recipe.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void anInventoryIsRendered() {
        commands.generateInventory(event(GEN_CHANNEL), 1, 9, "stone:1", null, "Chest", null, null, null, null);

        assertEquals("inventory.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void dialogueForOneNpcIsRendered() {
        commands.generateSingleDialogue(event(GEN_CHANNEL), "Bob", "Hello there", null, null, null, null, null, null);

        assertEquals("dialogue.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void dialogueForSeveralNpcsIsRendered() {
        commands.generateMultiDialogue(event(GEN_CHANNEL), "Steve, Alex", "0, Hi\n1, Hey", null, null, null, null, null, null);

        assertEquals("dialogue.png", onlyFile(lastEdit()).getName());
    }

    @Test
    void autocompleteOffersOnlyMatchingChoices() {
        assertChoices(commands.itemRarities(autocomplete("leg")), "leg");
        assertChoices(commands.tooltipSide(autocomplete("le")), "le");
        assertChoices(commands.powerStrengths(autocomplete("st")), "st");
        assertChoices(commands.overlayColors(autocomplete("red")), "red");
        assertChoices(commands.packIds(autocomplete("van")), "van");
        assertTrue(names(commands.itemNames(autocomplete("diamond_sw"))).contains("diamond_sword"));
        assertTrue(commands.tooltipStyles(autocomplete("")).size() <= 25);
    }

    @Test
    void historyIsSentAsAFile() throws IOException {
        DiscordUser member = member(false);
        member.setGeneratorHistory(new GeneratorHistory());
        member.getGeneratorHistory().addCommand("/gen text text: a");
        member.getGeneratorHistory().addCommand("/gen text text: b");

        commands.showHistory(DiscordFakes.slash(recorder, MEMBER));

        assertTrue(deferredHidden());
        FileUpload upload = DiscordFakes.lastCallItems(recorder, "editOriginalAttachments", FileUpload.class).getFirst();
        assertEquals("generator_history.txt", upload.getName());
        assertEquals("/gen text text: a\n\n/gen text text: b", new String(upload.getData().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void anEmptyHistorySaysSo() {
        member(false);

        commands.showHistory(DiscordFakes.slash(recorder, MEMBER));

        assertEquals("No history found", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void historyWithoutARecordSaysThereIsNone() {
        commands.showHistory(DiscordFakes.slash(recorder, MEMBER));

        assertEquals("No history found", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void repeatingACommandMovesItToTheEnd() {
        DiscordUser member = member(false);

        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);
        commands.generateTooltip(event(GEN_CHANNEL, "item"), "Name", "Lore", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        commands.generateText(event(GEN_CHANNEL), "hello", null, null, null, null, null, null, null, null);

        assertEquals(List.of("/gen item", COMMAND), member.getGeneratorHistory().getCommandHistory());
    }

    @Test
    void nbtTextIsUsedWhenNothingIsUploaded() throws IOException {
        assertEquals("{a:1}", GeneratorCommands.nbtInput("{a:1}", null));
    }

    @Test
    void anUploadWinsOverText() throws IOException {
        assertEquals("{b:2}", GeneratorCommands.nbtInput("{a:1}", upload("item.snbt", 5, "{b:2}")));
    }

    @Test
    void anUploadOverTheLimitIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> GeneratorCommands.nbtInput(null, upload("item.txt", GeneratorCommands.MAX_NBT_FILE_BYTES + 1, "x")));

        assertTrue(thrown.getMessage().startsWith("Attachment is too large!"));
    }

    @Test
    void anUploadAtTheLimitIsRead() throws IOException {
        assertEquals("{}", GeneratorCommands.nbtInput(null, upload("item.TXT", GeneratorCommands.MAX_NBT_FILE_BYTES, "{}")));
    }

    @Test
    void anUnsupportedFileTypeIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> GeneratorCommands.nbtInput(null, upload("item.png", 5, "{}")));

        assertEquals("Unsupported file type! Please upload a `.txt`, `.json`, `.snbt`, or `.nbt` file.", thrown.getMessage());
    }

    @Test
    void anEmptyUploadIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> GeneratorCommands.nbtInput(null, upload("item.json", 3, " \n ")));

        assertEquals("The uploaded file is empty!", thrown.getMessage());
    }

    @Test
    void aFailedDownloadIsAnErrorRatherThanBadInput() {
        GeneratorCommands.NbtUpload broken = new GeneratorCommands.NbtUpload("item.nbt", 5,
            () -> CompletableFuture.failedFuture(new IOException("connection reset")));

        assertThrows(IOException.class, () -> GeneratorCommands.nbtInput(null, broken));
    }

    @Test
    void choicesMatchTypedTextIgnoringCaseInOrder() {
        List<Command.Choice> choices = GeneratorCommands.toChoices(Stream.of("Diamond Sword", "stone", "DIAMOND"), "diaMond");

        assertEquals(List.of("Diamond Sword", "DIAMOND"), choices.stream().map(Command.Choice::getName).toList());
    }

    @Test
    void choicesSkipNamesDiscordWouldRejectAndStopAtTwentyFive() {
        Stream<String> candidates = Stream.concat(Stream.of("a".repeat(101)), IntStream.range(0, 30).mapToObj(index -> "a" + index));

        List<Command.Choice> choices = GeneratorCommands.toChoices(candidates, "a");

        assertEquals(25, choices.size());
        assertEquals("a0", choices.getFirst().getName());
    }

    @Test
    void historyOfAMemberWhoNeverGeneratedIsEmpty() {
        assertEquals(List.of(), GeneratorCommands.commandHistory(new DiscordUser(MEMBER)));
    }

    /** Through the event, {@code deferReply(hidden)} reaches the interaction as {@code deferReply().setEphemeral(hidden)}. */
    private boolean deferredHidden() {
        List<CallRecorder.Call> calls = recorder.calls();
        int defer = calls.stream().map(CallRecorder.Call::method).toList().lastIndexOf("deferReply");
        assertTrue(defer >= 0, "the reply was never deferred");
        return (boolean) calls.stream().skip(defer).filter(call -> call.method().equals("setEphemeral")).findFirst().orElseThrow().arg(0);
    }

    private CommandAutoCompleteInteractionEvent autocomplete(String typed) {
        OptionMapping focused = new OptionMapping(DataObject.empty().put("name", "option").put("type", OptionType.STRING.getKey()).put("value", typed),
            new TLongObjectHashMap<>(), null, null);
        CommandAutoCompleteInteraction interaction = recorder.fake(CommandAutoCompleteInteraction.class,
            Map.of("getFocusedOption", new AutoCompleteQuery(focused)));
        return new CommandAutoCompleteInteractionEvent(Fakes.of(JDA.class, Map.of()), 0, interaction);
    }

    private static List<String> names(List<Command.Choice> choices) {
        return choices.stream().map(Command.Choice::getName).toList();
    }

    private static void assertChoices(List<Command.Choice> choices, String typed) {
        assertFalse(choices.isEmpty(), "no choices for " + typed);
        assertTrue(choices.size() <= 25);
        names(choices).forEach(name -> assertTrue(name.toLowerCase(Locale.ROOT).contains(typed), name + " doesn't contain " + typed));
    }

    private DiscordUser member(boolean autoHide) {
        DiscordUser member = new DiscordUser(MEMBER);
        member.setAutoHideGenCommands(autoHide);
        users.seed(member);
        return member;
    }

    private void render(GeneratorCommands.Render render) {
        commands.generate(event(GEN_CHANNEL), null, "Couldn't render", render).join();
    }

    private SlashCommandInteractionEvent event(String channelId) {
        return event(channelId, "text");
    }

    private SlashCommandInteractionEvent event(String channelId, String subcommand) {
        return wrap(interaction(channelId, subcommand, recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private SlashCommandInteraction interactionWithHook(String channelId, InteractionHook hook) {
        return interaction(channelId, "text", hook);
    }

    // The event builds the command string from these parts, as JDA does
    private SlashCommandInteraction interaction(String channelId, String subcommand, InteractionHook hook) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getUser", DiscordFakes.user(MEMBER));
        answers.put("getHook", hook);
        answers.put("getChannelId", channelId);
        answers.put("getChannelIdLong", Long.parseLong(channelId));
        answers.put("getCommandType", Command.Type.SLASH);
        answers.put("getName", "gen");
        answers.put("getSubcommandName", subcommand);
        // JDA marks the interaction acknowledged as soon as the defer is queued
        answers.put("isAcknowledged", (CallRecorder.Answer) args -> recorder.called("deferReply"));
        return recorder.fake(SlashCommandInteraction.class, answers);
    }

    private static SlashCommandInteractionEvent wrap(SlashCommandInteraction interaction) {
        return new SlashCommandInteractionEvent(Fakes.of(JDA.class, Map.of()), 0, interaction);
    }

    private MessageEditData lastEdit() {
        return assertInstanceOf(MessageEditData.class, recorder.last("editOriginal").arg(0));
    }

    private static FileUpload onlyFile(MessageEditData reply) {
        assertEquals(1, reply.getFiles().size());
        return reply.getFiles().getFirst();
    }

    private static boolean startsWithPng(FileUpload upload) {
        try (InputStream data = upload.getData()) {
            byte[] start = data.readNBytes(PNG_SIGNATURE.length);
            return Arrays.equals(PNG_SIGNATURE, start);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static GeneratorCommands.NbtUpload upload(String fileName, int size, String content) {
        return new GeneratorCommands.NbtUpload(fileName, size,
            () -> CompletableFuture.completedFuture(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }
}
