package net.hypixel.nerdbot.app.generation.pack.update;

import com.google.gson.JsonParser;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployIndexParsingTest {

    private static final String DEPLOY_ID = "3fe209f6-82c6-44c1-85f4-311c30dfe50b";
    private static final DeployIndexReleaseSource SOURCE = new DeployIndexReleaseSource(
        "https://example.com/index.json", "https://resourcepacks.hypixel.net/SkyBlock/{deployId}/{format}.zip");

    private static Result<PackRelease, HttpException> parse(String json) {
        return SOURCE.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String failureMessage(String json) {
        return assertInstanceOf(Result.Failure.class, parse(json)).error().getMessage();
    }

    @Test
    void parsesOneVersionPerFormatWithTemplatedUrlsAndNoSha1() {
        PackRelease release = parse("""
            {"uuid": "%s", "pack_versions": [84, 88], "last_updated": 1790892317439}
            """.formatted(DEPLOY_ID)).orElseThrow();

        assertEquals(DEPLOY_ID, release.deployId());
        assertEquals(1790892317439L, release.lastUpdated());
        assertEquals(2, release.versions().size());
        PackReleaseVersion version = release.versions().get(1);
        assertEquals(88, version.packFormat());
        assertEquals(URI.create("https://resourcepacks.hypixel.net/SkyBlock/" + DEPLOY_ID + "/88.zip"), version.url());
        assertNull(version.sha1());
    }

    @Test
    void labelFallsBackToTheDeployKeyWithoutASha1() {
        PackReleaseVersion version = parse("{\"uuid\": \"%s\", \"pack_versions\": [88]}".formatted(DEPLOY_ID)).orElseThrow().versions().getFirst();

        assertEquals(DEPLOY_ID + "/88", version.label(DEPLOY_ID));
    }

    @Test
    void missingLastUpdatedIsZero() {
        assertEquals(0L, parse("{\"uuid\": \"%s\", \"pack_versions\": [88]}".formatted(DEPLOY_ID)).orElseThrow().lastUpdated());
    }

    @Test
    void surroundingWhitespaceInTheUuidIsIgnored() {
        assertEquals(DEPLOY_ID, parse("{\"uuid\": \" %s \", \"pack_versions\": [88]}".formatted(DEPLOY_ID)).orElseThrow().deployId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"pack_versions\": [88]}", "{\"uuid\": 5, \"pack_versions\": [88]}", "{\"uuid\": null, \"pack_versions\": [88]}"})
    void aMissingUuidFails(String json) {
        assertTrue(failureMessage(json).contains("no uuid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "../../etc/passwd", "3fe209f6-82c6-44c1-85f4-311c30dfe50b/../x"})
    void aUuidThatCouldChangeTheDownloadPathIsRefused(String uuid) {
        String message = failureMessage("{\"uuid\": \"%s\", \"pack_versions\": [88]}".formatted(uuid));

        assertTrue(message.contains("not a UUID"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{}", "88", "null"})
    void missingOrEmptyPackVersionsFail(String versions) {
        String message = failureMessage("{\"uuid\": \"%s\", \"pack_versions\": %s}".formatted(DEPLOY_ID, versions));

        assertTrue(message.contains("no pack_versions"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"88\"", "true", "{}"})
    void nonNumericFormatsFail(String format) {
        String message = failureMessage("{\"uuid\": \"%s\", \"pack_versions\": [%s]}".formatted(DEPLOY_ID, format));

        assertTrue(message.contains("not a number"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-3", "88.5", "3000000000"})
    void invalidFormatNumbersFail(String format) {
        String message = failureMessage("{\"uuid\": \"%s\", \"pack_versions\": [%s]}".formatted(DEPLOY_ID, format));

        assertTrue(message.contains("invalid pack format"), message);
    }

    @Test
    void failuresNameTheIndexUrl() {
        assertTrue(failureMessage("{}").contains("https://example.com/index.json"));
    }
}
