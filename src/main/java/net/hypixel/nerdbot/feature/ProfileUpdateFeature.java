package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.TimerTask;

@Log4j2
public class ProfileUpdateFeature extends BotFeature {

    @Override
    public void onStart() {
        this.timer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    NerdBotApp.getBot()
                        .getDatabase()
                        .getCollection("users", DiscordUser.class)
                        .find()
                        .into(new ArrayList<>())
                        .stream()
                        .filter(DiscordUser::isProfileAssigned)
                        .forEach(discordUser -> {
                            try {
                                MojangProfile mojangProfile = Util.getMojangProfile(discordUser.getMojangProfile().getUniqueId());
                                discordUser.setMojangProfile(mojangProfile);
                                Guild guild = Util.getMainGuild();

                                if (guild != null) {
                                    Member member = guild.retrieveMemberById(discordUser.getDiscordId()).complete();

                                    if (!member.getEffectiveName().toLowerCase().contains(mojangProfile.getUsername().toLowerCase())) {
                                        try {
                                            member.modifyNickname(mojangProfile.getUsername()).queue();
                                        } catch (HierarchyException hex) {
                                            log.warn("Unable to modify the nickname of " + member.getUser().getName() + " (" + member.getEffectiveName() + ") [" + member.getId() + "].");
                                        }
                                    }
                                }
                            } catch (Exception ignore) { }
                        });
                }
            },
            Duration.of(30, ChronoUnit.MINUTES).toMillis(),
            Duration.of(NerdBotApp.getBot().getConfig().getMojangUsernameCache(), ChronoUnit.HOURS).toMillis()
        );
    }

    @Override
    public void onEnd() {
        this.timer.cancel();
    }

}
