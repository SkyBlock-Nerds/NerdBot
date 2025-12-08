package net.hypixel.nerdbot.generator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.data.Gemstone;
import net.hypixel.nerdbot.generator.data.Flavor;
import net.hypixel.nerdbot.generator.data.Icon;
import net.hypixel.nerdbot.generator.data.ParseType;
import net.hypixel.nerdbot.generator.data.Stat;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Attempts to reverse map rendered lore/name text back into generator placeholders using configured data
 */
@Slf4j
public class PlaceholderReverseMapper {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([^}]+)}");
    private static final String DEFAULT_CAPTURE = "[^\\n]+?";

    private record ReplacementRule(Pattern pattern, ReplacementProvider provider) {
    }

    @FunctionalInterface
    private interface ReplacementProvider {
        String provide(Matcher matcher);
    }

    private final List<ReplacementRule> rules;

    public PlaceholderReverseMapper() {
        this.rules = new ArrayList<>();
        this.rules.addAll(buildStatRules());
        this.rules.addAll(buildGemstoneRules());
        this.rules.addAll(buildIconRules());
        this.rules.addAll(buildFlavorRules());

        log.info("Initialized PlaceholderReverseMapper with {} rules", rules.size());
    }

    public String mapPlaceholders(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String normalized = TextWrapper.normalizeNewlines(input.replace(ChatFormat.SECTION_SYMBOL, ChatFormat.AMPERSAND_SYMBOL));
        String[] lines = normalized.split("\n", -1);
        List<String> mapped = new ArrayList<>(lines.length);

        for (String line : lines) {
            mapped.add(applyRules(line));
        }

        return String.join("\n", mapped);
    }

    private String applyRules(String line) {
        String result = line;

        for (ReplacementRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(result);
            boolean replaced = false;
            StringBuilder stringBuilder = new StringBuilder();

            while (matcher.find()) {
                String replacement = rule.provider().provide(matcher);
                matcher.appendReplacement(stringBuilder, Matcher.quoteReplacement(replacement));
                replaced = true;
            }

            if (replaced) {
                matcher.appendTail(stringBuilder);
                result = stringBuilder.toString();
            }
        }

        return result;
    }

    private List<ReplacementRule> buildStatRules() {
        List<ReplacementRule> statRules = new ArrayList<>();

        for (Stat stat : Stat.getStats()) {
            ParseType parseType = ParseType.byName(stat.getParseType());

            if (parseType == null) {
                log.warn("Missing parse type for stat '{}'", stat.getName());
                continue;
            }

            if (parseType.getFormatWithDetails() != null) {
                statRules.add(buildStatRule(stat, parseType.getFormatWithDetails()));
            }

            if (parseType.getFormatWithoutDetails() != null) {
                statRules.add(buildStatRule(stat, parseType.getFormatWithoutDetails()));
            }
        }

        return statRules;
    }

    private List<ReplacementRule> buildFlavorRules() {
        List<ReplacementRule> flavorRules = new ArrayList<>();

        for (Flavor flavor : Flavor.getFlavors()) {
            ParseType parseType = ParseType.byName(flavor.getParseType());

            if (parseType == null) {
                log.warn("Missing parse type for flavor text '{}'", flavor.getName());
                continue;
            }

            if (parseType.getFormatWithDetails() != null) {
                flavorRules.add(buildFlavorRule(flavor, parseType.getFormatWithDetails()));
            }

            if (parseType.getFormatWithoutDetails() != null) {
                flavorRules.add(buildFlavorRule(flavor, parseType.getFormatWithoutDetails()));
            }
        }

        return flavorRules;
    }

    private ReplacementRule buildFlavorRule(Flavor flavor, String format) {
        PatternBuildResult result = buildPattern(format, token -> resolveFlavorToken(flavor, token));

        return new ReplacementRule(result.pattern(), matcher -> {
            List<String> parts = new ArrayList<>();
            if (!result.captureOrder().isEmpty()) {
                for (int i = 0; i < result.captureOrder().size(); i++) {
                    int groupIndex = i + 1;
                    if (groupIndex <= matcher.groupCount()) {
                        parts.add(matcher.group(groupIndex));
                    }
                }
            }

            String placeholder = "%%" + flavor.getName();
            if (!parts.isEmpty()) {
                placeholder += ":" + String.join(":", parts).trim();
            }

            return placeholder + "%%";
        });
    }

    private ReplacementRule buildStatRule(Stat stat, String format) {
        PatternBuildResult result = buildPattern(format, token -> resolveStatToken(stat, token));

        return new ReplacementRule(result.pattern(), matcher -> {
            List<String> parts = new ArrayList<>();
            if (!result.captureOrder().isEmpty()) {
                for (int i = 0; i < result.captureOrder().size(); i++) {
                    int groupIndex = i + 1;
                    if (groupIndex <= matcher.groupCount()) {
                        parts.add(matcher.group(groupIndex));
                    }
                }
            }

            String placeholder = "%%" + stat.getName();
            if (!parts.isEmpty()) {
                placeholder += ":" + String.join(":", parts).trim();
            }

            return placeholder + "%%";
        });
    }

    private List<ReplacementRule> buildIconRules() {
        return Icon.getIcons().stream()
            .map(icon -> {
                String iconText = safeString(icon.getIcon());
                String regex = "(" + Pattern.quote(iconText) + ")+";
                Pattern pattern = Pattern.compile(regex);

                return new ReplacementRule(pattern, matcher -> {
                    String matched = matcher.group(0);
                    int count = matched.length() / iconText.length();
                    String placeholder = "%%" + icon.getName();

                    if (count > 1) {
                        placeholder += ":" + count;
                    }

                    return placeholder + "%%";
                });
            })
            .collect(Collectors.toList());
    }

    private List<ReplacementRule> buildGemstoneRules() {
        List<ReplacementRule> gemstoneRules = new ArrayList<>();

        for (Gemstone gemstone : Gemstone.getGemstones()) {
            String icon = safeString(gemstone.getIcon());
            String formattedIcon = safeString(gemstone.getFormattedIcon());

            for (Map.Entry<String, String> entry : safeMap(gemstone.getFormattedTiers()).entrySet()) {
                String tierName = entry.getKey();
                String format = entry.getValue();
                if (format == null) {
                    continue;
                }

                String iconPattern = Pattern.quote(icon);
                String formattedIconPattern = Pattern.quote(formattedIcon.replace(ChatFormat.SECTION_SYMBOL, ChatFormat.AMPERSAND_SYMBOL));
                String replaced = format.replace(ChatFormat.SECTION_SYMBOL, ChatFormat.AMPERSAND_SYMBOL)
                    .replace("%s", "(?:" + formattedIconPattern + "|" + iconPattern + ")");

                Pattern pattern = Pattern.compile(escapeAmpersands(replaced));
                gemstoneRules.add(new ReplacementRule(pattern, matcher -> "%%" + gemstone.getName() + ":" + tierName + "%%"));
            }
        }

        return gemstoneRules;
    }

    private record PatternBuildResult(Pattern pattern, List<String> captureOrder) {
    }

    private PatternBuildResult buildPattern(String format, TokenResolver resolver) {
        StringBuilder regex = new StringBuilder();
        List<String> captureOrder = new ArrayList<>();

        Matcher matcher = TOKEN_PATTERN.matcher(format);
        int last = 0;

        while (matcher.find()) {
            String literal = format.substring(last, matcher.start());
            regex.append(escapeLiteral(literal));

            String key = matcher.group(1);
            String value = resolver.resolve(key);
            if (value != null && !value.isEmpty()) {
                regex.append(escapeLiteral(value));
            } else {
                captureOrder.add(key);
                regex.append("(").append(DEFAULT_CAPTURE).append(")");
            }

            last = matcher.end();
        }

        regex.append(escapeLiteral(format.substring(last)));
        regex.append("(?:[§&]r)?");

        String regexString = escapeAmpersands(regex.toString());
        return new PatternBuildResult(Pattern.compile(regexString, Pattern.CASE_INSENSITIVE), captureOrder);
    }

    private static String escapeLiteral(String text) {
        return Pattern.quote(text);
    }

    private String resolveFlavorToken(Flavor flavor, String token) {
        if ("ampersand".equalsIgnoreCase(token)) {
            return String.valueOf(ChatFormat.AMPERSAND_SYMBOL);
        }

        ChatFormat chatFormat = ChatFormat.of(token.toUpperCase());
        if (chatFormat != null) {
            return String.valueOf(chatFormat.getCode());
        }

        try {
            String methodName = "get" + Character.toUpperCase(token.charAt(0)) + token.substring(1);
            Method method = Flavor.class.getMethod(methodName);
            Object value = method.invoke(flavor);
            if (value instanceof ChatFormat) {
                return String.valueOf(((ChatFormat) value).getCode());
            }
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String escapeAmpersands(String input) {
        return input.replace("\\Q&\\E", "[§&]");
    }

    @FunctionalInterface
    private interface TokenResolver {
        String resolve(String token);
    }

    private String resolveStatToken(Stat stat, String token) {
        if ("ampersand".equalsIgnoreCase(token)) {
            return String.valueOf(ChatFormat.AMPERSAND_SYMBOL);
        }

        ChatFormat chatFormat = ChatFormat.of(token.toUpperCase());
        if (chatFormat != null) {
            return String.valueOf(chatFormat.getCode());
        }

        try {
            String methodName = "get" + Character.toUpperCase(token.charAt(0)) + token.substring(1);
            Method method = Stat.class.getMethod(methodName);
            Object value = method.invoke(stat);
            if (value instanceof ChatFormat cf) {
                return String.valueOf(cf.getCode());
            }
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> safeMap(Map<String, String> map) {
        return map == null ? new LinkedHashMap<>() : map;
    }
}
