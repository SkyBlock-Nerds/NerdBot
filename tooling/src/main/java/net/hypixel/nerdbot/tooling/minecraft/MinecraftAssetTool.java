package net.hypixel.nerdbot.tooling.minecraft;

import net.hypixel.nerdbot.tooling.ToolingConstants;
import net.hypixel.nerdbot.tooling.spritesheet.OverlayColorConfigGenerator;
import net.hypixel.nerdbot.tooling.spritesheet.OverlayGenerator;
import net.hypixel.nerdbot.tooling.spritesheet.SpritesheetGenerator;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Unified CLI tool for Minecraft asset operations.
 * <p>
 * Supports downloading assets, rendering items/blocks, and generating spritesheets
 * either individually or as a complete pipeline.
 * <p>
 * Usage:
 * <pre>{@code
 * # Run complete pipeline
 * java MinecraftAssetTool --all
 *
 * # Run specific steps
 * java MinecraftAssetTool --download --render --spritesheet
 *
 * # Download only
 * java MinecraftAssetTool --download --version=1.21.4
 *
 * # Skip certain steps in pipeline
 * java MinecraftAssetTool --all --skip-download
 * }</pre>
 * <p>
 * Maven profiles:
 * <pre>{@code
 * mvn compile -P asset-pipeline -pl tooling
 * mvn compile -P download-assets -pl tooling
 * mvn compile -P render-items -pl tooling
 * mvn compile -P generate-spritesheet -pl tooling
 * }</pre>
 */
public class MinecraftAssetTool {

    private boolean runDownload = false;
    private boolean runRender = false;
    private boolean runSpritesheet = false;
    private boolean runOverlays = false;
    private boolean skipDownload = false;
    private boolean skipRender = false;
    private boolean debug = false;

    private String minecraftVersion = null;
    private int renderSize = 256;
    private boolean keepJar = true;
    private boolean forceReclone = false;
    private boolean forceRebuild = false;

    public static void main(String[] args) {
        MinecraftAssetTool tool = new MinecraftAssetTool();

        for (String arg : args) {
            switch (arg) {
                case "--all", "--pipeline" -> {
                    tool.runDownload = true;
                    tool.runRender = true;
                    tool.runSpritesheet = true;
                    tool.runOverlays = true;
                }
                case "--download" -> tool.runDownload = true;
                case "--render" -> tool.runRender = true;
                case "--spritesheet" -> tool.runSpritesheet = true;
                case "--overlays" -> tool.runOverlays = true;
                case "--skip-download" -> tool.skipDownload = true;
                case "--skip-render" -> tool.skipRender = true;
                case "--debug" -> {
                    tool.debug = true;
                    ToolingConstants.setDebugEnabled(true);
                }
                case "--force-clone" -> tool.forceReclone = true;
                case "--force-build" -> tool.forceRebuild = true;
                case "--help" -> {
                    printHelp();
                    return;
                }
                default -> {
                    if (arg.startsWith("--version=")) {
                        tool.minecraftVersion = arg.substring("--version=".length());
                    } else if (arg.startsWith("--size=")) {
                        tool.renderSize = Integer.parseInt(arg.substring("--size=".length()));
                    }
                }
            }
        }

        if (!tool.runDownload && !tool.runRender && !tool.runSpritesheet && !tool.runOverlays) {
            tool.runDownload = true;
            tool.runRender = true;
            tool.runSpritesheet = true;
            tool.runOverlays = true;
        }

        try {
            tool.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (tool.debug) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Minecraft Asset Tool");
        System.out.println("====================");
        System.out.println("Unified tool for downloading, rendering, and generating Minecraft asset spritesheets.");
        System.out.println();
        System.out.println("Usage: java MinecraftAssetTool [steps] [options]");
        System.out.println();
        System.out.println("Steps (can combine multiple):");
        System.out.println("  --all, --pipeline    Run complete pipeline (download + render + spritesheet + overlays)");
        System.out.println("  --download           Download Minecraft assets from Mojang");
        System.out.println("  --render             Render items/blocks using MinecraftRenderer (.NET)");
        System.out.println("  --spritesheet        Generate spritesheet from rendered images");
        System.out.println("  --overlays           Generate overlay spritesheet and color configs from assets");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version=<ver>      Minecraft version (default: latest release)");
        System.out.println("  --size=<pixels>      Render size in pixels (default: 256)");
        System.out.println("  --skip-download      Skip download step (use cached assets)");
        System.out.println("  --skip-render        Skip render step (use existing renders)");
        System.out.println("  --force-clone        Force re-clone MinecraftRenderer repository");
        System.out.println("  --force-build        Force rebuild MinecraftRenderer");
        System.out.println("  --debug              Enable debug output");
        System.out.println("  --help               Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java MinecraftAssetTool --all                    # Full pipeline");
        System.out.println("  java MinecraftAssetTool --all --skip-download    # Pipeline without download");
        System.out.println("  java MinecraftAssetTool --download --version=1.21.4");
        System.out.println("  java MinecraftAssetTool --render --size=512");
        System.out.println("  java MinecraftAssetTool --spritesheet");
        System.out.println();
        System.out.println("Maven profiles:");
        System.out.println("  mvn compile -P asset-pipeline -pl tooling");
        System.out.println("  mvn compile -P download-assets -pl tooling [-Dmc.version=1.21.4]");
        System.out.println("  mvn compile -P render-items -pl tooling");
        System.out.println("  mvn compile -P generate-spritesheet -pl tooling");
        System.out.println();
        System.out.println("Prerequisites:");
        System.out.println("  - .NET SDK 9.0+ for rendering (https://dotnet.microsoft.com/download)");
        System.out.println("  - Git for cloning MinecraftRenderer");
    }

    public void run() throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Minecraft Asset Tool");
        System.out.println("=".repeat(60));

        printConfig();

        if (runDownload && !skipDownload) {
            runDownloadStep();
        } else if (runDownload) {
            System.out.println("\n[Download] Skipped (--skip-download)");
        }

        if (runRender && !skipRender) {
            runRenderStep();
        } else if (runRender) {
            System.out.println("\n[Render] Skipped (--skip-render)");
        }

        if (runSpritesheet) {
            runSpritesheetStep();
        }

        if (runOverlays) {
            runOverlaysStep();
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Complete!");
        System.out.println("=".repeat(60));
    }

    private void printConfig() {
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Minecraft version: " + (minecraftVersion != null ? minecraftVersion : "latest"));
        System.out.println("  Render size: " + renderSize + "px");
        System.out.println("  Steps: " + buildStepsString());
        if (debug) {
            System.out.println("  Debug: enabled");
        }
    }

    private String buildStepsString() {
        StringBuilder sb = new StringBuilder();
        if (runDownload) sb.append(skipDownload ? "download(skipped) " : "download ");
        if (runRender) sb.append(skipRender ? "render(skipped) " : "render ");
        if (runSpritesheet) sb.append("spritesheet ");
        if (runOverlays) sb.append("overlays ");
        return sb.toString().trim();
    }

    private void runDownloadStep() throws IOException, InterruptedException {
        System.out.println("\n[Download] Downloading Minecraft assets...");
        System.out.println("-".repeat(40));

        AssetDownloader downloader = new AssetDownloader(
            minecraftVersion,
            ToolingConstants.MINECRAFT_ASSETS,
            keepJar,
            true    // clean
        );
        downloader.setDebug(debug);
        int count = downloader.download();
        this.minecraftVersion = downloader.getResolvedVersion();

        System.out.println("[Download] Extracted " + count + " files");
    }

    private void runRenderStep() throws IOException, InterruptedException {
        System.out.println("\n[Render] Running MinecraftRenderer...");
        System.out.println("-".repeat(40));

        if (!isDotNetAvailable()) {
            System.out.println("WARNING: .NET SDK not found. Skipping render step.");
            System.out.println("Install .NET SDK 9.0+ from https://dotnet.microsoft.com/download");
            return;
        }

        if (Files.exists(ToolingConstants.RENDERED_ITEMS)) {
            System.out.println("Cleaning previous renders: " + ToolingConstants.RENDERED_ITEMS);
            ToolingConstants.deleteDirectory(ToolingConstants.RENDERED_ITEMS);
        }

        ItemRenderer renderer = new ItemRenderer();
        renderer.setDebug(debug);

        try {
            renderer.setup(forceReclone, forceRebuild);
            renderer.renderAllItems(ToolingConstants.RENDERED_ITEMS, minecraftVersion, renderSize);
        } catch (Exception e) {
            System.out.println("WARNING: Renderer failed: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    private void runSpritesheetStep() {
        System.out.println("\n[Spritesheet] Generating spritesheets...");
        System.out.println("-".repeat(40));

        SpritesheetGenerator.main(new String[]{"--input=" + ToolingConstants.RENDERED_ITEMS});
    }

    private void runOverlaysStep() throws IOException {
        System.out.println("\n[Overlays] Generating overlay spritesheets and color configs...");
        System.out.println("-".repeat(40));

        new OverlayGenerator().generate();
        new OverlayColorConfigGenerator(minecraftVersion).generate();
    }

    private boolean isDotNetAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("dotnet", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

}
