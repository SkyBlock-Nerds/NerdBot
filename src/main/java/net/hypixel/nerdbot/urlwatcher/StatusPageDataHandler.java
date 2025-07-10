package net.hypixel.nerdbot.urlwatcher;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.urlwatcher.URLWatcher;
import net.hypixel.nerdbot.cache.ChannelCache;
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

        ChannelCache.getTextChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getBotSpamChannelId()).ifPresentOrElse(textChannel -> {
            Util.createTempFileAsync("status_page_data_old_content.json", NerdBotApp.GSON.toJson(oldContent))
                .thenCompose(oldFile -> 
                    Util.createTempFileAsync("status_page_data_new_content.json", NerdBotApp.GSON.toJson(newContent))
                        .thenCompose(newFile ->
                            Util.createTempFileAsync("status_page_data_changed_values.json", NerdBotApp.GSON.toJson(changedValues))
                                .thenAccept(changedFile -> {
                                    List<FileUpload> files = List.of(
                                        FileUpload.fromData(oldFile),
                                        FileUpload.fromData(newFile),
                                        FileUpload.fromData(changedFile)
                                    );
                                    
                                    textChannel.sendMessage("**[STATUS PAGE DATA HANDLER]** Status page data changed!").addFiles(files).queue();
                                    log.info("Uploaded status page data to Discord!");
                                })
                        )
                )
                .exceptionally(throwable -> {
                    log.error("Failed to upload status page data to Discord!", throwable);
                    return null;
                });
        }, () -> log.warn("No bot-spam channel set, cannot send status update!"));
    }
}
