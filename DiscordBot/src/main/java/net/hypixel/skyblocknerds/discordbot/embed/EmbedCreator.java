package net.hypixel.skyblocknerds.discordbot.embed;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;

@Getter
@AllArgsConstructor
public abstract class EmbedCreator {

    private final DiscordUser discordUser;

    public abstract EmbedBuilder create();

}
