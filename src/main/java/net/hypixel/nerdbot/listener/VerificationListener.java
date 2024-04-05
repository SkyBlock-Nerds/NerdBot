package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.command.ProfileCommands;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
public class VerificationListener {

    @SubscribeEvent
    public void onButtonClickEvent(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null) {
            return;
        }

        if (event.getButton().getId().startsWith("verification")) {
            String[] parts = event.getButton().getId().split("-");
            String action = parts[1];
            String memberId = parts[2];
            ButtonStyle buttonStyle = ButtonStyle.SECONDARY;
            String buttonLabel = "Unknown";
            List<Button> buttons = new ArrayList<>();
            boolean updateButtons = true;

            if (action.equals("kick")) {
                int remainingClicks = Integer.parseInt(parts[3]);
                remainingClicks--;

                if (remainingClicks == 0) {
                    updateButtons = false;

                    event.getInteraction()
                        .reply("Are you sure you want to kick <@" + memberId + ">?")
                        .setEphemeral(true)
                        .addComponents(ActionRow.of(
                            Button.of(
                                ButtonStyle.DANGER,
                                String.format("verification-kicknow-%s", memberId),
                                "Kick Now"
                            )
                        ))
                        .queue();
                } else {
                    event.getInteraction().deferEdit().complete();
                    buttonStyle = ButtonStyle.DANGER;
                    buttonLabel = "Denied";
                    buttons.add(Button.of(ButtonStyle.DANGER, String.format("verification-kick-%s-%s", memberId, remainingClicks), String.format("Kick (%s)", remainingClicks)));
                }
            } else if (action.equals("kicknow")) {
                updateButtons = false;
                Guild guild = Util.getMainGuild();
                Member member = guild.retrieveMemberById(memberId).complete();
                String displayName = Util.getDisplayName(member.getUser());
                guild.kick(UserSnowflake.fromId(memberId)).complete();

                event.getInteraction()
                    .replyEmbeds(
                        new EmbedBuilder()
                            .setTitle("Member Kicked")
                            .setDescription(displayName + " has been kicked from the server after failing verification.")
                            .setColor(Color.RED)
                            .build()
                    )
                    .queue();
            } else {
                event.getInteraction().deferEdit().complete();

                if (action.equals("deny")) {
                    buttonStyle = ButtonStyle.DANGER;
                    buttonLabel = "Denied";
                    buttons.add(Button.of(ButtonStyle.DANGER, String.format("verification-kick-%s-3", memberId), "Kick (3)"));
                } else if (action.equals("accept")) {
                    MojangProfile mojangProfile = ProfileCommands.VERIFY_CACHE.getIfPresent(memberId);

                    if (mojangProfile == null) {
                        buttonStyle = ButtonStyle.DANGER;
                        buttonLabel = "Expired";
                    } else {
                        Guild guild = Util.getMainGuild();
                        Member member = guild.retrieveMemberById(memberId).complete();

                        if (member == null) {
                            buttonLabel = "Left Server";
                        } else {
                            ProfileCommands.updateMojangProfile(member, mojangProfile);
                            buttonStyle = ButtonStyle.SUCCESS;
                            buttonLabel = "Accepted";
                        }
                    }
                }

                ProfileCommands.VERIFY_CACHE.invalidate(memberId);
            }

            if (updateButtons) {
                buttons.add(Button.of(buttonStyle, "verification-done", buttonLabel).asDisabled());
                Collections.reverse(buttons);
                event.getHook().editOriginalComponents(ActionRow.of(buttons)).complete();
            }
        }
    }
}
