package net.hypixel.skyblocknerds.discordbot.embed.profile;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.embed.EmbedCreator;
import net.hypixel.skyblocknerds.utilities.StringUtils;
import net.hypixel.skyblocknerds.utilities.discord.DiscordTimestamp;

import java.awt.Color;

public class MemberDiscordProfileEmbed extends EmbedCreator {

    public MemberDiscordProfileEmbed(DiscordUser discordUser) {
        super(discordUser);
    }

    @Override
    public EmbedBuilder create() {
        EmbedBuilder builder = new EmbedBuilder().setTitle("Your Discord Profile");
        Member member = DiscordBot.getPrimaryGuild().getMemberById(getDiscordUser().getDiscordId());

        if (member == null) {
            builder.setDescription("Couldn't load your Discord profile!")
                .setColor(Color.RED);
        } else {
            builder.addField("Display Name", member.getEffectiveName(), true)
                .addField("Join Date", DiscordTimestamp.toShortDate(member.getTimeJoined().toInstant().toEpochMilli()), true)
                .addField("Badges", StringUtils.COMMA_SEPARATED_DECIMAL_FORMAT.format(getDiscordUser().getBadges().size()), true)
                .addField("Language", getDiscordUser().getLanguage().getName(), true)
                .setThumbnail(member.getUser().getAvatarUrl())
                .setColor(Color.GREEN);
        }

        return builder;
    }
}
