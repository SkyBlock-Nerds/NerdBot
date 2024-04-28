package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.components.Components;
import com.freya02.botcommands.api.components.InteractionConstraints;
import com.freya02.botcommands.api.components.annotations.JDAButtonListener;
import com.freya02.botcommands.api.components.annotations.JDASelectionMenuListener;
import com.freya02.botcommands.api.components.event.ButtonEvent;
import com.freya02.botcommands.api.components.event.StringSelectionEvent;
import com.freya02.botcommands.api.pagination.menu.ChoiceMenu;
import com.freya02.botcommands.api.pagination.menu.ChoiceMenuBuilder;
import com.freya02.botcommands.api.pagination.menu.Menu;
import com.freya02.botcommands.api.pagination.menu.MenuBuilder;
import com.freya02.botcommands.api.pagination.paginator.Paginator;
import com.freya02.botcommands.api.pagination.paginator.PaginatorBuilder;
import com.freya02.botcommands.api.utils.ButtonContent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.List;

public class ComponentTestCommands extends ApplicationCommand {

    private static final String SELECTION_HANDLER_NAME = "selectionHandler";

    @JDASlashCommand(name = "button", description = "Test button components", defaultLocked = true)
    public void buttonTest(GuildSlashEvent event) {
        Button button = Button.primary("test-button", "Click me!");
        Button button2 = Button.danger("test-button2", "Click me!");
        Button button3 = Button.secondary("test-button3", "Click me!");
        Button button4 = Button.success("test-button4", "Click me!");

        event.reply("Here's a button!").addActionRow(List.of(button, button2, button3, button4)).queue();
    }

    @JDAButtonListener(name = "test-button")
    public void buttonListener(ButtonEvent event) {
        event.reply("You clicked the test button!").queue();
    }

    @JDASlashCommand(scope = CommandScope.GLOBAL, name = "selectionmenutest", description = "Shows how menus works", defaultLocked = true)
    public void selectionMenuTest(GlobalSlashEvent event) {
        event.reply("Selection menu test")
            // A persistent selection menu, still works after a bot restarts
            .addActionRow(Components.stringSelectionMenu(SELECTION_HANDLER_NAME).addOption("Option 1", "Value 1").addOption("Option 2", "Value 2").addOption("Option 3", "Value 3").setPlaceholder("Select a value").build())
            // A lambda selection menu, won't work after a bot restart
            .addActionRow(Components.stringSelectionMenu(e -> e.reply("Selected a value in a lambda selection menu: " + e.getValues()).setEphemeral(true).queue()).addOption("Option 1", "Value 1").addOption("Option 2", "Value 2").addOption("Option 3", "Value 3").setPlaceholder("Select a value").build()).setEphemeral(true).queue();
    }

    @JDASelectionMenuListener(name = SELECTION_HANDLER_NAME)
    public void stringSelectionMenu(StringSelectionEvent event) {
        event.reply("Selected a value in a persistent selection menu: " + event.getValues()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "choicemenutest", defaultLocked = true)
    public void choiceMenuTest(GuildSlashEvent event) {
        final List<Guild> entries = new ArrayList<>(event.getJDA().getGuilds());

        // The choice menu is like a menu except the user has buttons to choose an entry,
        //      and you can wait or use a callback to use the user choice
        final ChoiceMenu<Guild> paginator = new ChoiceMenuBuilder<>(entries)
            // Only the caller can use the choice menu
            .setConstraints(InteractionConstraints.ofUsers(event.getUser()))
            // There must be no delete button as the message is ephemeral
            .useDeleteButton(false)
            // Transforms each entry (a Guild) into this text
            .setTransformer(guild -> String.format("%s (%s)", guild.getName(), guild.getId()))
            // Show only 1 entry per page
            .setMaxEntriesPerPage(1)
            // Set the entry (row) prefix
            // This will then display as
            // - GuildName (GuildId)
            .setRowPrefixSupplier((entry, maxEntry) -> " - ")
            // This gets called when the user chooses an entry via the buttons
            // First callback parameter is the button event and the second is the chosen entry (the Guild)
            .setCallback((btnEvt, guild) -> {
                // Edit the message with a Message so everything is replaced, instead of just the content
                btnEvt.editMessage("You chose the guild '" + guild.getName() + "' !")
                    .setReplace(true)
                    .queue();
            })
            // This determines what the buttons look like
            .setButtonContentSupplier((item, index) -> ButtonContent.withString(String.valueOf(index + 1)))
            .build();

        // You must send the menu as a message
        event.reply(MessageCreateData.fromEditData(paginator.get()))
            .setEphemeral(true)
            .queue();
    }

    // TODO figure out why this throws an exception
    @JDASlashCommand(name = "paginatedmenutest", defaultLocked = true)
    public void menuTest(GuildSlashEvent event) {
        final List<Guild> entries = new ArrayList<>(event.getJDA().getGuilds());

        // Only the caller can use the menu
        // There must be no delete button as the message is ephemeral
        final Menu<Guild> paginator = new MenuBuilder<>(entries)
            // Only the caller can use the choice menu
            .setConstraints(InteractionConstraints.ofUsers(event.getUser()))
            // There must be no delete button as the message is ephemeral
            .useDeleteButton(false)
            // Transforms each entry (a Guild) into this text
            .setTransformer(guild -> String.format("%s (%s)", guild.getName(), guild.getId()))
            // Show only 1 entry per page
            .setMaxEntriesPerPage(1)
            // Set the entry (row) prefix
            // This will then display as
            // - GuildName (GuildId)
            .setRowPrefixSupplier((entry, maxEntry) -> " - ")
            .build();

        // You must send the menu as a message
        event.reply(MessageCreateData.fromEditData(paginator.get()))
            .setEphemeral(true)
            .queue();
    }

    @JDASlashCommand(name = "paginatormenutest2", defaultLocked = true)
    public void run(GuildSlashEvent event) {
        final List<EmbedBuilder> embedBuilders = new ArrayList<>();

        // Let's suppose you generated embeds like in JDA-U, so you'd have a collection of embeds to present
        for (int i = 0; i < 5; i++) {
            embedBuilders.add(new EmbedBuilder().setTitle("Page #" + (i + 1)));
        }

        final Paginator paginator = new PaginatorBuilder()
            // Only the caller can use the choice menu
            .setConstraints(InteractionConstraints.ofUsers(event.getUser()))
            // There must be no delete button as the message is ephemeral
            .useDeleteButton(false)
            // There is 5 pages for the paginator
            .setMaxPages(5)
            .setPaginatorSupplier((instance, messageBuilder, components, page) -> embedBuilders.get(page).build())
            .build();

        // You must send the paginator as a message
        event.reply(MessageCreateData.fromEditData(paginator.get()))
            .setEphemeral(true)
            .queue();
    }
}
