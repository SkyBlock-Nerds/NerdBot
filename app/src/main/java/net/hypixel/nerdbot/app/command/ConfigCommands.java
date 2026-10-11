package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.config.ConfigEditor;
import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.MetricsConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.metrics.MetricsServer;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** /config and /metrics: viewing, reloading and editing the bot's config. */
@Slf4j
public class ConfigCommands {

    static final String SAVE_FAILED_MESSAGE = "Couldn't write the config file, so nothing was changed. Check the logs for details.";

    private final Provider<NerdBotConfig> config;
    private final ConfigStore store;
    private final ConfigEditor editor;
    private final MetricsServer metrics;
    private final JDA jda;

    @Inject
    public ConfigCommands(Provider<NerdBotConfig> config, ConfigStore store, ConfigEditor editor, MetricsServer metrics, JDA jda) {
        this.config = config;
        this.store = store;
        this.editor = editor;
        this.metrics = metrics;
        this.jda = jda;
    }

    @SlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void showConfig(SlashCommandInteractionEvent event) {
        show(event);
    }

    void show(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        byte[] json = DataSerialization.PRETTY_GSON.toJson(config.get()).getBytes(StandardCharsets.UTF_8);
        hook.editOriginalAttachments(FileUpload.fromData(json, "config.json")).queue();
    }

    @SlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void reloadConfig(SlashCommandInteractionEvent event) {
        reload(event);
    }

    void reload(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        try {
            store.reload();
        } catch (IllegalStateException exception) {
            CommandReplies.fail(hook, "The config file couldn't be loaded, so the old config is still in use. Check the logs for details.", exception);
            return;
        }

        applyLive(config.get());
        hook.editOriginal("Reloaded the config file!").queue();
    }

    @SlashCommand(name = "config", subcommand = "edit", description = "Edit the config file", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void editConfig(SlashCommandInteractionEvent event, @SlashOption String key, @SlashOption String value) {
        edit(event, key, value);
    }

    void edit(IReplyCallback event, String key, String value) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        ConfigEditor.Result result;
        try {
            result = editor.edit(key, value);
        } catch (IOException exception) {
            CommandReplies.fail(hook, SAVE_FAILED_MESSAGE, exception);
            return;
        }

        switch (result) {
            case ConfigEditor.Rejected rejected -> hook.editOriginal(rejected.reason()).queue();
            case ConfigEditor.Saved saved -> {
                applyLive(saved.config());
                log.info("{} (ID: {}) set {} in the config", event.getUser().getName(), event.getUser().getId(), key);
                hook.editOriginal("Updated `" + key + "`. The change is live and saved.").queue();
            }
        }
    }

    @SlashCommand(name = "metrics", subcommand = "toggle", description = "Toggle metrics collection", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void toggleMetrics(SlashCommandInteractionEvent event) {
        toggle(event);
    }

    void toggle(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        NerdBotConfig current = config.get();
        MetricsConfig metricsConfig = current.getMetricsConfig();
        boolean enabled = !metricsConfig.isEnabled();

        metricsConfig.setEnabled(enabled);
        try {
            store.save(current);
        } catch (IOException exception) {
            metricsConfig.setEnabled(!enabled);
            CommandReplies.fail(hook, SAVE_FAILED_MESSAGE, exception);
            return;
        }

        metrics.setEnabled(enabled);
        hook.editOriginal("Toggled metrics: " + enabled).queue();
    }

    /** What the bot does with a config when it starts, done again for one swapped in while it runs. */
    private void applyLive(NerdBotConfig newConfig) {
        jda.getPresence().setActivity(Activity.of(newConfig.getActivityType().toJda(), newConfig.getActivity()));
        metrics.setEnabled(newConfig.getMetricsConfig().isEnabled());
    }
}
