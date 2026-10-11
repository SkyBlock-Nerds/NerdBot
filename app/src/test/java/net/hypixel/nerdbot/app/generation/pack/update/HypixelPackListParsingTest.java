package net.hypixel.nerdbot.app.generation.pack.update;

import com.google.gson.JsonParser;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypixelPackListParsingTest {

    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    private static Result<List<HypixelPack>, HttpException> parse(String json) {
        return HttpHypixelPackApiClient.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String failureMessage(Result<List<HypixelPack>, HttpException> result) {
        return assertInstanceOf(Result.Failure.class, result).error().getMessage();
    }

    @Test
    void parsesEveryPackAndVersion() {
        List<HypixelPack> packs = parse("""
            {"success": true, "packs": [{
              "id": "SkyBlock", "deployId": "d-1", "lastUpdated": 1790000000000,
              "versions": [
                {"packFormat": 46, "hash": "%s", "url": "https://resourcepacks.hypixel.net/a.zip"},
                {"packFormat": 48, "hash": "%s", "url": "https://resourcepacks.hypixel.net/b.zip"}
              ]
            }]}
            """.formatted(SHA1, SHA1)).orElseThrow();

        HypixelPack pack = packs.getFirst();
        assertEquals("SkyBlock", pack.id());
        assertEquals("d-1", pack.deployId());
        assertEquals(1790000000000L, pack.lastUpdated());
        assertEquals(new HypixelPackVersion(48, SHA1, URI.create("https://resourcepacks.hypixel.net/b.zip")), pack.versions().get(1));
    }

    @Test
    void uppercaseHashesAreLowercased() {
        HypixelPack pack = parse("""
            {"success": true, "packs": [{"id": "SkyBlock", "versions": [
              {"packFormat": 46, "hash": "%s", "url": "https://x/a.zip"}]}]}
            """.formatted(SHA1.toUpperCase())).orElseThrow().getFirst();

        assertEquals(SHA1, pack.versions().getFirst().sha1());
    }

    @Test
    void missingDeployIdAndLastUpdatedGetDefaults() {
        HypixelPack pack = parse("""
            {"success": true, "packs": [{"id": "SkyBlock", "versions": []}]}
            """).orElseThrow().getFirst();

        assertEquals("unknown", pack.deployId());
        assertEquals(0L, pack.lastUpdated());
    }

    @Test
    void aResponseWithoutSuccessFails() {
        assertTrue(failureMessage(parse("{\"success\": false, \"packs\": []}")).contains("did not report success"));
        assertTrue(failureMessage(parse("{\"packs\": []}")).contains("did not report success"));
    }

    @Test
    void aResponseWithoutAPacksArrayFails() {
        assertTrue(failureMessage(parse("{\"success\": true, \"packs\": {}}")).contains("no packs array"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-hash", "0123", "zz23456789abcdef0123456789abcdef01234567"})
    void malformedHashesFail(String hash) {
        String message = failureMessage(parse("""
            {"success": true, "packs": [{"id": "SkyBlock", "versions": [
              {"packFormat": 46, "hash": "%s", "url": "https://x/a.zip"}]}]}
            """.formatted(hash)));

        assertTrue(message.contains("malformed hash"), message);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositivePackFormatsFail(int format) {
        String message = failureMessage(parse("""
            {"success": true, "packs": [{"id": "SkyBlock", "versions": [
              {"packFormat": %d, "hash": "%s", "url": "https://x/a.zip"}]}]}
            """.formatted(format, SHA1)));

        assertTrue(message.contains("pack format"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"success\": true, \"packs\": [5]}",
        "{\"success\": true, \"packs\": [{\"id\": \"SkyBlock\"}]}",
        "{\"success\": true, \"packs\": [{\"id\": \"SkyBlock\", \"versions\": [{\"packFormat\": \"x\"}]}]}",
        "{\"success\": true, \"packs\": [{\"id\": \"SkyBlock\", \"versions\": [{\"packFormat\": 46, \"hash\": \"0123456789abcdef0123456789abcdef01234567\", \"url\": \"not a url\"}]}]}"
    })
    void wrongShapesFailInsteadOfThrowing(String json) {
        assertTrue(failureMessage(parse(json)).contains("malformed"));
    }

    @Test
    void hypixelSourcePicksThePackByIdAndKeepsItsHashes() {
        HypixelPack other = new HypixelPack("Other", "d-0", 1, List.of(new HypixelPackVersion(46, SHA1, URI.create("https://x/o.zip"))));
        HypixelPack skyblock = new HypixelPack("SkyBlock", "d-1", 2, List.of(new HypixelPackVersion(48, SHA1, URI.create("https://x/s.zip"))));
        HypixelApiReleaseSource source = new HypixelApiReleaseSource(() -> Result.success(List.of(other, skyblock)), "SkyBlock");

        PackRelease release = source.latest().orElseThrow();

        assertEquals("d-1", release.deployId());
        assertEquals(new PackReleaseVersion(48, URI.create("https://x/s.zip"), SHA1), release.versions().getFirst());
    }

    @Test
    void hypixelSourceFailsWhenThePackIsMissingOrHasNoVersions() {
        HypixelPack empty = new HypixelPack("SkyBlock", "d-1", 2, List.of());

        Result<PackRelease, HttpException> missing = new HypixelApiReleaseSource(() -> Result.success(List.of()), "SkyBlock").latest();
        Result<PackRelease, HttpException> noVersions = new HypixelApiReleaseSource(() -> Result.success(List.of(empty)), "SkyBlock").latest();

        assertTrue(assertInstanceOf(Result.Failure.class, missing).error().getMessage().contains("no entry named SkyBlock"));
        assertTrue(assertInstanceOf(Result.Failure.class, noVersions).error().getMessage().contains("lists no versions"));
    }

    @Test
    void hypixelSourcePassesOnAFetchFailure() {
        Result<PackRelease, HttpException> result = new HypixelApiReleaseSource(() -> Result.failure(new HttpException("timeout")), "SkyBlock").latest();

        assertTrue(assertInstanceOf(Result.Failure.class, result).error().getMessage().contains("timeout"));
    }
}
