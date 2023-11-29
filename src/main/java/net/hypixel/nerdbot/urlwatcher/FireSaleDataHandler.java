package net.hypixel.nerdbot.urlwatcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Tuple;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import net.hypixel.nerdbot.api.urlwatcher.URLWatcher;

import java.awt.Color;
import java.util.Date;
import java.util.List;

@Log4j2
public class FireSaleDataHandler implements URLWatcher.DataHandler {

    @Override
    public void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        ChannelConfig config = NerdBotApp.getBot().getConfig().getChannelConfig();

        ChannelManager.getChannelById(config.getAnnouncementChannelId()).ifPresentOrElse(textChannel -> {
            changedValues.forEach(tuple -> {
                if (tuple.value1().equals("sales")) {
                    JsonArray array = JsonParser.parseString(String.valueOf(tuple.value3())).getAsJsonArray();

                    if (array.isEmpty()) {
                        return;
                    }

                    EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle("New Fire Sale!")
                        .setTimestamp(new Date().toInstant())
                        .setColor(Color.GREEN);

                    array.forEach(jsonElement -> {
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        String itemId = jsonObject.get("item_id").getAsString().replace("PET_SKIN_", "");
                        DiscordTimestamp start = new DiscordTimestamp(jsonObject.get("start").getAsLong());
                        DiscordTimestamp end = new DiscordTimestamp(jsonObject.get("end").getAsLong());
                        int amount = jsonObject.get("amount").getAsInt();
                        int price = jsonObject.get("price").getAsInt();
                        StringBuilder stringBuilder = new StringBuilder();

                        stringBuilder.append("Starts: ").append(start.toLongDateTime()).append(" (").append(start.toRelativeTimestamp()).append(")\n")
                            .append("Ends: ").append(end.toLongDateTime()).append(" (").append(end.toRelativeTimestamp()).append(")\n")
                            .append("Amount: ").append(Util.COMMA_SEPARATED_FORMAT.format(amount)).append("\n")
                            .append("Price: ").append(Util.COMMA_SEPARATED_FORMAT.format(price));

                        embedBuilder.addField(itemId, stringBuilder.toString(), false);
                    });

                    MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder().setEmbeds(embedBuilder.build());

                    RoleManager.getPingableRoleByName("Fire Sale Alerts").ifPresent(pingableRole -> {
                        messageCreateBuilder.addContent(RoleManager.formatPingableRoleAsMention(pingableRole) + "\n\n");
                    });

                    textChannel.sendMessage(messageCreateBuilder.build()).queue();
                }
            });
        }, () -> {
            throw new IllegalStateException("Could not find announcement channel!");
        });
    }
}
