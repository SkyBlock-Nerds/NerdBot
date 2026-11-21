package net.hypixel.nerdbot.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IAutoCompleteCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

/**
 * Sits between JDA and the command library and drops interactions from guilds the bot doesn't
 * serve.
 *
 * <p>The library checks a command's permissions against the member in the guild the command was
 * run in. A bot's config and database are shared by every guild it is in, so without this an
 * administrator of any other guild, such as an emoji server, could run the admin commands against
 * the main guild's data. Buttons, select menus, modals and autocomplete get no permission check
 * from the library at all, so they are held to the same rule here.
 *
 * <p>Interactions outside a guild (direct messages) are passed on; commands decide for themselves
 * whether they work there.
 */
@Slf4j
final class GuildInteractionGate implements EventListener {

    static final String REFUSAL = "This bot's commands can't be used in this server.";

    private final EventListener commands;
    private final Predicate<Guild> served;

    GuildInteractionGate(EventListener commands, Predicate<Guild> served) {
        this.commands = commands;
        this.served = served;
    }

    @Override
    @SubscribeEvent
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GenericInteractionCreateEvent interactionEvent && !isServed(interactionEvent.getGuild())) {
            refuse(interactionEvent.getInteraction());
            return;
        }

        commands.onEvent(event);
    }

    private boolean isServed(Guild guild) {
        return guild == null || served.test(guild);
    }

    private static void refuse(Interaction interaction) {
        log.warn("Refused an interaction from user {} in guild {}, which this bot doesn't serve",
            interaction.getUser().getId(), interaction.getGuild().getId());

        if (interaction instanceof IReplyCallback callback) {
            callback.reply(REFUSAL).setEphemeral(true).queue();
        } else if (interaction instanceof IAutoCompleteCallback callback) {
            callback.replyChoices(List.of()).queue();
        }
    }
}
