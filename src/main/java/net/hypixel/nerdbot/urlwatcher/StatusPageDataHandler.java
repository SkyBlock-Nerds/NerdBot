package net.hypixel.nerdbot.urlwatcher;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.urlwatcher.URLWatcher;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.util.Tuple;
import net.hypixel.nerdbot.util.Util;

import java.io.IOException;
import java.util.List;

@Log4j2
public class StatusPageDataHandler implements URLWatcher.DataHandler {

    @Override
    public void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        log.info("Status page data changed!");
        log.info("  Old content: " + oldContent);
        log.info("  New content: " + newContent);
        log.info("  Changed values: " + changedValues.toString());

        ChannelManager.getLogChannel().ifPresentOrElse(textChannel -> {
            try (MessageCreateData messageCreateData = MessageCreateData.fromContent("**[STATUS PAGE DATA HANDLER]** Status page data changed!")) {
                messageCreateData.getFiles().addAll(List.of(
                    FileUpload.fromData(Util.createTempFile("status_page_data_old_content.json", NerdBotApp.GSON.toJson(oldContent))),
                    FileUpload.fromData(Util.createTempFile("status_page_data_new_content.json", NerdBotApp.GSON.toJson(newContent)))
                ));

                textChannel.sendMessage(messageCreateData).queue();
            } catch (IOException e) {
                log.error("Failed to upload status page data to Discord!", e);
            }
        }, () -> log.warn("No log channel set, cannot send status update!"));
    }
}
