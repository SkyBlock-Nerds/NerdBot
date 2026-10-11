package net.hypixel.nerdbot.app.testsupport;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Shared pieces for command tests that drive handlers through {@link CallRecorder} fakes. */
public final class DiscordFakes {

    private DiscordFakes() {
    }

    public static User user(String id) {
        return Fakes.of(User.class, Map.of("getId", id));
    }

    public static SlashCommandInteraction slash(CallRecorder recorder, String userId) {
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", user(userId)));
    }

    public static ButtonInteraction button(CallRecorder recorder, String userId, String componentId) {
        return recorder.fake(ButtonInteraction.class, Map.of("getUser", user(userId), "getComponentId", componentId));
    }

    public static StringSelectInteraction select(CallRecorder recorder, String userId, String componentId, String value) {
        return recorder.fake(StringSelectInteraction.class, Map.of("getUser", user(userId), "getComponentId", componentId, "getValues", List.of(value)));
    }

    /** A filled-in text input, as a submitted modal reports it. */
    public static ModalMapping modalValue(String inputId, String value) {
        return new ModalMapping(DataObject.empty().put("custom_id", inputId).put("value", value).put("type", 4));
    }

    public static String lastEditText(CallRecorder recorder) {
        return (String) recorder.last("editOriginal").arg(0);
    }

    public static List<ActionRow> lastComponents(CallRecorder recorder) {
        return lastCallItems(recorder, "setComponents", ActionRow.class);
    }

    public static List<MessageEmbed> lastEmbeds(CallRecorder recorder, String method) {
        return lastCallItems(recorder, method, MessageEmbed.class);
    }

    /**
     * The items passed to the last call of {@code method}, whether as varargs or as one collection;
     * JDA's varargs overloads hand on to the collection ones.
     */
    public static <T> List<T> lastCallItems(CallRecorder recorder, String method, Class<T> type) {
        List<Object> args = recorder.last(method).args();
        if (args.size() == 1 && args.getFirst() instanceof Collection<?> items) {
            return items.stream().map(type::cast).toList();
        }
        return args.stream().map(type::cast).toList();
    }

    public static StringSelectMenu selectMenu(ActionRow row) {
        return (StringSelectMenu) row.getActionComponents().getFirst();
    }
}
