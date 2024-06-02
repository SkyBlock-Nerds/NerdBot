package net.hypixel.skyblocknerds.discordbot.embed.profile;

import net.dv8tion.jda.api.EmbedBuilder;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.objects.user.minecraft.MinecraftProfile;
import net.hypixel.skyblocknerds.discordbot.embed.EmbedCreator;

import java.awt.Color;

public class MemberMinecraftProfileEmbed extends EmbedCreator {

    public MemberMinecraftProfileEmbed(DiscordUser discordUser) {
        super(discordUser);
    }

    @Override
    public EmbedBuilder create() {
        EmbedBuilder builder = new EmbedBuilder().setTitle("Your Minecraft Account");

        if (!getDiscordUser().hasMinecraftProfile()) {
            builder.setDescription("Link your Minecraft account by using the `/link` command!")
                .setColor(Color.RED);
        } else {
            MinecraftProfile minecraftProfile = getDiscordUser().getMinecraftProfile();
            builder.addField("Username", minecraftProfile.getUsername(), false)
                .addField("UUID", minecraftProfile.getUniqueId().toString(), false)
                .setThumbnail("https://crafatar.com/avatars/" + minecraftProfile.getUniqueId().toString() + "?size=128&overlay=true")
                .setColor(Color.GREEN);
        }

        return builder;
    }
}
