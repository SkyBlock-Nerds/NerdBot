package net.hypixel.nerdbot.service.orangejuice;

import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.TextGeneratorRequest;

import java.io.IOException;

public class GenerateTextService extends GeneratorBaseService {

    public byte[] generateText(TextGeneratorRequest data) throws InterruptedException, IOException {
        return generateImage("/text", data);
    }
}