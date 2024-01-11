package net.hypixel.nerdbot.urlwatcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Tuple;
import net.hypixel.nerdbot.api.urlwatcher.URLWatcher;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

@Log4j2
public class FireSaleDataHandler implements URLWatcher.DataHandler {

    @Override
    public void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        ChannelConfig config = NerdBotApp.getBot().getConfig().getChannelConfig();

        log.info("Fire sale data changed!");

        ChannelCache.getTextChannelById(config.getAnnouncementChannelId()).ifPresentOrElse(textChannel -> {
            log.debug("Changed values: " + changedValues);

            JsonArray oldSaleData = JsonUtil.parseString(oldContent).getAsJsonObject().getAsJsonArray("sales");
            JsonArray newSaleData = JsonUtil.parseString(newContent).getAsJsonObject().getAsJsonArray("sales");

            for (int i = 0; i < oldSaleData.size(); i++) {
                for (int j = 0; j < newSaleData.size(); j++) {
                    JsonObject oldObject = oldSaleData.get(i).getAsJsonObject();
                    JsonObject newObject = newSaleData.get(j).getAsJsonObject();

                    if (isEqual(oldObject, newObject)) {
                        newSaleData.remove(j);
                        log.debug("Removed " + oldSaleData.get(i).getAsJsonObject().get("item_id").getAsString() + " from the new sale data list.");
                        break;
                    }
                }
            }

            if (newSaleData.isEmpty()) {
                log.info("No new sale data found!");
                return;
            }

            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("New Fire Sale!")
                .setTimestamp(Instant.now())
                .setColor(Color.GREEN);

            newSaleData.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .forEachOrdered(jsonObject -> {
                    String itemId = jsonObject.get("item_id").getAsString();
                    DiscordTimestamp startTime = new DiscordTimestamp(jsonObject.get("start").getAsLong());
                    DiscordTimestamp endTime = new DiscordTimestamp(jsonObject.get("end").getAsLong());
                    int amount = jsonObject.get("amount").getAsInt();
                    int price = jsonObject.get("price").getAsInt();

                    log.info("Found new sale data for item " + itemId + "!");

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Start Time: ").append(startTime.toLongDateTime())
                        .append(" (").append(startTime.toRelativeTimestamp()).append(")").append("\n");
                    stringBuilder.append("End Time: ").append(endTime.toLongDateTime())
                        .append(" (").append(endTime.toRelativeTimestamp()).append(")").append("\n");
                    stringBuilder.append("Amount: ").append(Util.COMMA_SEPARATED_FORMAT.format(amount)).append("x\n");
                    stringBuilder.append("Price: ").append(Util.COMMA_SEPARATED_FORMAT.format(price)).append(" SkyBlock Gems");

                    embedBuilder.addField(itemId, stringBuilder.toString(), false);
                });

            MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder().setEmbeds(embedBuilder.build());

            RoleManager.getPingableRoleByName("Fire Sale Alerts").ifPresent(pingableRole -> {
                messageCreateBuilder.addContent(RoleManager.formatPingableRoleAsMention(pingableRole) + "\n\n");
            });

            textChannel.sendMessage(messageCreateBuilder.build()).queue();
        }, () -> log.error("Announcement channel not found!"));
    }

    private boolean isEqual(JsonObject oldObject, JsonObject newObject) {
        return oldObject.get("item_id").getAsString().equals(newObject.get("item_id").getAsString());
    }
}
