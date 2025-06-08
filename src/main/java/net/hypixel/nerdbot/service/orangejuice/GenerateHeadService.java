package net.hypixel.nerdbot.service.orangejuice;

import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.HeadGeneratorRequest;

import java.io.IOException;

public class GenerateHeadService extends GeneratorBaseService {

    public byte[] generateHead(HeadGeneratorRequest data) throws IOException, InterruptedException {
        return generateImage("/head", data);
    }
}