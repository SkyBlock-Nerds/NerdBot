package net.hypixel.nerdbot.tooling.minecraft;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.hypixel.nerdbot.tooling.ToolingConstants;
import net.hypixel.nerdbot.tooling.minecraft.colorextractor.DyeColorVisitor;
import net.hypixel.nerdbot.tooling.minecraft.colorextractor.EffectFieldMapper;
import net.hypixel.nerdbot.tooling.minecraft.colorextractor.EffectRegistryVisitor;
import net.hypixel.nerdbot.tooling.minecraft.colorextractor.EffectSubclassVisitor;
import net.hypixel.nerdbot.tooling.minecraft.colorextractor.PotionVisitor;
import org.objectweb.asm.ClassReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts dye and potion color values from Minecraft JAR bytecode.
 */
public class ColorExtractor {

    private final Path jarPath;
    private final Path mappingsPath;
    private final Map<String, String> classToObfuscated = new HashMap<>();

    private boolean debug = false;

    public ColorExtractor(Path jarPath, Path mappingsPath) {
        this.jarPath = jarPath;
        this.mappingsPath = mappingsPath;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        ToolingConstants.setDebugEnabled(debug);
    }

    public ExtractedColors extract() throws IOException {
        System.out.println("Parsing mappings...");
        parseMappings();
        ToolingConstants.printDebug("Found " + classToObfuscated.size() + " class mappings");

        ExtractedColors result = new ExtractedColors();

        String dyeColorClass = classToObfuscated.get("net.minecraft.world.item.DyeColor");
        if (dyeColorClass != null) {
            System.out.println("Extracting DyeColor values from: " + dyeColorClass);
            result.dyeColors = extractDyeColors(dyeColorClass);
            System.out.println("  Found " + result.dyeColors.size() + " dye colors");
        } else {
            System.err.println("WARNING: Could not find DyeColor class mapping");
        }

        String mobEffectsClass = classToObfuscated.get("net.minecraft.world.effect.MobEffects");
        String mobEffectClass = classToObfuscated.get("net.minecraft.world.effect.MobEffect");
        if (mobEffectsClass != null && mobEffectClass != null) {
            System.out.println("Extracting MobEffect colors from: " + mobEffectsClass);
            result.effectColors = extractEffectColors(mobEffectsClass);

            if (result.effectColors.isEmpty()) {
                System.out.println("  Trying alternate method: scanning MobEffect subclasses...");
                result.effectColors = scanEffectSubclasses(mobEffectClass);
            }

            System.out.println("  Found " + result.effectColors.size() + " effect colors");
        } else {
            System.err.println("WARNING: Could not find MobEffects class mapping");
        }

        String potionsClass = classToObfuscated.get("net.minecraft.world.item.alchemy.Potions");

        Set<String> effectsWithPotions = new HashSet<>();
        if (potionsClass != null && mobEffectsClass != null) {
            System.out.println("Extracting potion->effect mappings from: " + potionsClass);
            effectsWithPotions = findPotionEffects(potionsClass, mobEffectsClass);
            System.out.println("  Found " + effectsWithPotions.size() + " effects with potion forms");
        } else {
            System.err.println("WARNING: Could not find Potions or MobEffects class mapping");
        }

        result.potionColors = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : result.effectColors.entrySet()) {
            if (effectsWithPotions.contains(entry.getKey())) {
                result.potionColors.put(entry.getKey(), entry.getValue());
            }
        }

        result.potionNames = effectsWithPotions;
        System.out.println("  Filtered to " + result.potionColors.size() + " potion colors");

        if (debug) {
            Set<String> effectOnly = new HashSet<>(result.effectColors.keySet());
            effectOnly.removeAll(effectsWithPotions);
            System.out.println("  Effects without potions: " + effectOnly);
        }

        return result;
    }

    private void parseMappings() throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();

        try (BufferedReader reader = Files.newBufferedReader(mappingsPath)) {
            MappingReader.read(reader, tree);
        }

        for (MappingTree.ClassMapping classMapping : tree.getClasses()) {
            String srcName = classMapping.getSrcName();
            String dstName = classMapping.getDstName(0);

            if (srcName != null && dstName != null) {
                classToObfuscated.put(
                    srcName.replace('/', '.'),
                    dstName.replace('/', '.')
                );
            }
        }
    }

    private Map<String, DyeColorData> extractDyeColors(String obfuscatedClass) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            String classPath = obfuscatedClass.replace('.', '/') + ".class";
            ZipEntry entry = zip.getEntry(classPath);
            if (entry == null) {
                System.err.println("Class not found in JAR: " + classPath);
                return new LinkedHashMap<>();
            }

            try (InputStream is = zip.getInputStream(entry)) {
                ClassReader reader = new ClassReader(is);
                DyeColorVisitor visitor = new DyeColorVisitor(ToolingConstants::printDebug);
                reader.accept(visitor, 0);
                return visitor.getColors();
            }
        }
    }

    private Map<String, Integer> extractEffectColors(String mobEffectsClass) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            String classPath = mobEffectsClass.replace('.', '/') + ".class";
            ZipEntry entry = zip.getEntry(classPath);
            if (entry == null) {
                System.err.println("Class not found in JAR: " + classPath);
                return new LinkedHashMap<>();
            }

            try (InputStream is = zip.getInputStream(entry)) {
                ClassReader reader = new ClassReader(is);
                EffectRegistryVisitor visitor = new EffectRegistryVisitor(ToolingConstants::printDebug);
                reader.accept(visitor, 0);
                return visitor.getColors();
            }
        }
    }

    private Set<String> findPotionEffects(String potionsClass, String mobEffectsClass) throws IOException {
        String potionsInternal = potionsClass.replace('.', '/');
        String mobEffectsInternal = mobEffectsClass.replace('.', '/');

        Map<String, String> fieldToEffectName = new HashMap<>();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            ZipEntry mobEffectsEntry = zip.getEntry(mobEffectsInternal + ".class");
            if (mobEffectsEntry != null) {
                try (InputStream is = zip.getInputStream(mobEffectsEntry)) {
                    ClassReader reader = new ClassReader(is);
                    EffectFieldMapper fieldMapper = new EffectFieldMapper(ToolingConstants::printDebug);
                    reader.accept(fieldMapper, 0);
                    fieldToEffectName = fieldMapper.getFieldToName();
                    ToolingConstants.printDebug("Mapped " + fieldToEffectName.size() + " MobEffects fields to names");
                }
            }

            ZipEntry potionsEntry = zip.getEntry(potionsInternal + ".class");
            if (potionsEntry != null) {
                try (InputStream is = zip.getInputStream(potionsEntry)) {
                    ClassReader reader = new ClassReader(is);
                    PotionVisitor visitor = new PotionVisitor(mobEffectsInternal, fieldToEffectName, ToolingConstants::printDebug);
                    reader.accept(visitor, 0);
                    return visitor.getEffectsWithPotions();
                }
            }
        }

        return new HashSet<>();
    }

    private Map<String, Integer> scanEffectSubclasses(String mobEffectClass) throws IOException {
        Map<String, Integer> colors = new LinkedHashMap<>();
        String mobEffectInternal = mobEffectClass.replace('.', '/');

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                try (InputStream is = zip.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(is);
                    if (mobEffectInternal.equals(reader.getSuperName())) {
                        EffectSubclassVisitor visitor = new EffectSubclassVisitor(mobEffectInternal);
                        reader.accept(visitor, 0);

                        if (visitor.getEffectName() != null && visitor.getColor() != 0) {
                            colors.put(visitor.getEffectName(), visitor.getColor());
                            ToolingConstants.printDebug("Found effect: " + visitor.getEffectName() + " -> " + visitor.getColor());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return colors;
    }
}