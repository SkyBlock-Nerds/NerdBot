package net.hypixel.nerdbot.util.skyblock;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.Color;
import java.util.HashMap;

@Getter
@AllArgsConstructor
public enum MCColor {
    BLACK('0', new Color(0, 0, 0), new Color(0, 0, 0)),
    DARK_BLUE('1', new Color(0, 0, 170), new Color(0, 0, 42)),
    DARK_GREEN('2', new Color(0, 170, 0), new Color(0, 42, 0)),
    DARK_AQUA('3', new Color(0, 170, 170), new Color(0, 42, 42)),
    DARK_RED('4', new Color(170, 0, 0), new Color(42, 0, 0)),
    DARK_PURPLE('5', new Color(170, 0, 170), new Color(42, 0, 42)),
    GOLD('6', new Color(255, 170, 0), new Color(42, 42, 0)),
    GRAY('7', new Color(170, 170, 170), new Color(42, 42, 42)),
    DARK_GRAY('8', new Color(85, 85, 85), new Color(21, 21, 21)),
    BLUE('9', new Color(85, 85, 255), new Color(21, 21, 63)),
    GREEN('a', new Color(85, 255, 85), new Color(21, 63, 21)),
    AQUA('b', new Color(85, 255, 255), new Color(21, 63, 63)),
    RED('c', new Color(255, 85, 85), new Color(63, 21, 21)),
    LIGHT_PURPLE('d', new Color(255, 85, 255), new Color(63, 21, 63)),
    YELLOW('e', new Color(255, 255, 85), new Color(63, 63, 21)),
    WHITE('f', new Color(255, 255, 255), new Color(63, 63, 63)),
    BOLD('l', new Color(255, 255, 255), new Color(255, 255, 85)),
    STRIKETHROUGH('m', new Color(255, 255, 255), new Color(255, 255, 255)),
    UNDERLINE('n', new Color(255, 255, 255), new Color(255, 255, 255)),
    ITALIC('o', new Color(255, 255, 255), new Color(85, 85, 255)),
    OBFUSCATED('k', new Color(255, 255, 255), new Color(85, 85, 255)),
    RESET('r', new Color(170, 170, 170), new Color(42, 42, 42));

    public static final MCColor[] VALUES = values();
    public static final HashMap<String, Color> FIREWORK_COLORS = new HashMap<>() {{
        put("red", new Color(11743532));
        put("orange", new Color(15435844));
        put("yellow", new Color(14602026));
        put("lime", new Color(4312372));
        put("green", new Color(3887386));
        put("light_blue", new Color(6719955));
        put("cyan", new Color(2651799));
        put("blue", new Color(2437522));
        put("purple", new Color(8073150));
        put("magenta", new Color(12801229));
        put("pink", new Color(14188952));
        put("white", new Color(15790320));
        put("light_gray", new Color(11250603));
        put("gray", new Color(4408131));
        put("black", new Color(1973019));
        put("brown", new Color(5320730));
    }};
    public static final HashMap<String, Color> LEATHER_ARMOR_COLORS = new HashMap<>() {{
        put("red", new Color(11546150));
        put("orange", new Color(16351261));
        put("yellow", new Color(16701501));
        put("lime", new Color(8439583));
        put("green", new Color(6192150));
        put("light_blue", new Color(3847130));
        put("cyan", new Color(1481884));
        put("blue", new Color(3949738));
        put("purple", new Color(8991416));
        put("magenta", new Color(13061821));
        put("pink", new Color(15961002));
        put("white", new Color(16383998));
        put("light_gray", new Color(10329495));
        put("gray", new Color(4673362));
        put("black", new Color(1908001));
        put("brown", new Color(8606770));
    }};
    public static final HashMap<String, Color> POTION_COLORS = new HashMap<>() {{
        put("water", new Color(-13017142));
        put("uncraftable", new Color(-261892));
        put("night_vision", new Color(-14671708));
        put("invisibility", new Color(-8288875));
        put("jump_boost", new Color(-14418867));
        put("fire_resistance", new Color(-1532613));
        put("swiftness", new Color(-8473910));
        put("slowness", new Color(-10719613));
        put("turtle_master", new Color(-8954524));
        put("water_breathing", new Color(-13675364));
        put("instant_health", new Color(-252636));
        put("instant_damage", new Color(-12318199));
        put("poison", new Color(-11561422));
        put("regeneration", new Color(-3055954));
        put("strength", new Color(-6937308));
        put("weakness", new Color(-11973047));
        put("luck", new Color(-13329408));
        put("slow_falling", new Color(-199467));
    }};
    public static final HashMap<String, Color[]> SPAWN_EGG_COLORS = new HashMap<>() {{
        put("allay", new Color[]{new Color(56063), new Color(44543)});
        put("axolotl", new Color[]{new Color(16499171), new Color(10890612)});
        put("bat", new Color[]{new Color(4996656), new Color(986895)});
        put("bee", new Color[]{new Color(15582019), new Color(4400155)});
        put("blaze", new Color[]{new Color(16167425), new Color(16775294)});
        put("camel", new Color[]{new Color(16565097), new Color(13341495)});
        put("cat", new Color[]{new Color(15714446), new Color(9794134)});
        put("cave_spider", new Color[]{new Color(803406), new Color(11013646)});
        put("chicken", new Color[]{new Color(10592673), new Color(16711680)});
        put("cod", new Color[]{new Color(12691306), new Color(15058059)});
        put("cow", new Color[]{new Color(4470310), new Color(10592673)});
        put("creeper", new Color[]{new Color(894731), new Color(0)});
        put("dolphin", new Color[]{new Color(2243405), new Color(16382457)});
        put("donkey", new Color[]{new Color(5457209), new Color(8811878)});
        put("drowned", new Color[]{new Color(9433559), new Color(7969893)});
        put("elder_guardian", new Color[]{new Color(13552826), new Color(7632531)});
        put("ender_dragon", new Color[]{new Color(1842204), new Color(14711290)});
        put("enderman", new Color[]{new Color(1447446), new Color(0)});
        put("endermite", new Color[]{new Color(1447446), new Color(7237230)});
        put("evoker", new Color[]{new Color(9804699), new Color(1973274)});
        put("fox", new Color[]{new Color(14005919), new Color(13396256)});
        put("frog", new Color[]{new Color(13661252), new Color(16762748)});
        put("ghast", new Color[]{new Color(16382457), new Color(12369084)});
        put("glow_squid", new Color[]{new Color(611926), new Color(8778172)});
        put("goat", new Color[]{new Color(10851452), new Color(5589310)});
        put("guardian", new Color[]{new Color(5931634), new Color(15826224)});
        put("hoglin", new Color[]{new Color(13004373), new Color(6251620)});
        put("horse", new Color[]{new Color(12623485), new Color(15656192)});
        put("husk", new Color[]{new Color(7958625), new Color(15125652)});
        put("iron_golem", new Color[]{new Color(14405058), new Color(7643954)});
        put("llama", new Color[]{new Color(12623485), new Color(10051392)});
        put("magma_cube", new Color[]{new Color(3407872), new Color(16579584)});
        put("mooshroom", new Color[]{new Color(10489616), new Color(12040119)});
        put("mule", new Color[]{new Color(1769984), new Color(5321501)});
        put("ocelot", new Color[]{new Color(15720061), new Color(5653556)});
        put("panda", new Color[]{new Color(15198183), new Color(1776418)});
        put("parrot", new Color[]{new Color(894731), new Color(16711680)});
        put("phantom", new Color[]{new Color(4411786), new Color(8978176)});
        put("pig", new Color[]{new Color(15771042), new Color(14377823)});
        put("piglin", new Color[]{new Color(10051392), new Color(16380836)});
        put("piglin_brute", new Color[]{new Color(5843472), new Color(16380836)});
        put("pillager", new Color[]{new Color(5451574), new Color(9804699)});
        put("polar_bear", new Color[]{new Color(15658718), new Color(14014157)});
        put("pufferfish", new Color[]{new Color(16167425), new Color(3654642)});
        put("rabbit", new Color[]{new Color(10051392), new Color(7555121)});
        put("ravager", new Color[]{new Color(7697520), new Color(5984329)});
        put("salmon", new Color[]{new Color(10489616), new Color(951412)});
        put("sheep", new Color[]{new Color(15198183), new Color(16758197)});
        put("shulker", new Color[]{new Color(9725844), new Color(5060690)});
        put("silverfish", new Color[]{new Color(7237230), new Color(3158064)});
        put("skeleton", new Color[]{new Color(12698049), new Color(4802889)});
        put("skeleton_horse", new Color[]{new Color(6842447), new Color(15066584)});
        put("slime", new Color[]{new Color(5349438), new Color(8306542)});
        put("sniffer", new Color[]{new Color(9840944), new Color(5085536)});
        put("snow_golem", new Color[]{new Color(14283506), new Color(8496292)});
        put("spider", new Color[]{new Color(3419431), new Color(11013646)});
        put("squid", new Color[]{new Color(2243405), new Color(7375001)});
        put("stray", new Color[]{new Color(6387319), new Color(14543594)});
        put("strider", new Color[]{new Color(10236982), new Color(5065037)});
        put("tadpole", new Color[]{new Color(7164733), new Color(1444352)});
        put("trader_llama", new Color[]{new Color(15377456), new Color(4547222)});
        put("tropical_fish", new Color[]{new Color(15690005), new Color(16775663)});
        put("turtle", new Color[]{new Color(15198183), new Color(44975)});
        put("vex", new Color[]{new Color(8032420), new Color(15265265)});
        put("villager", new Color[]{new Color(5651507), new Color(12422002)});
        put("vindicator", new Color[]{new Color(9804699), new Color(2580065)});
        put("wandering_trader", new Color[]{new Color(4547222), new Color(15377456)});
        put("warden", new Color[]{new Color(1001033), new Color(3790560)});
        put("witch", new Color[]{new Color(3407872), new Color(5349438)});
        put("wither", new Color[]{new Color(1315860), new Color(5075616)});
        put("wither_skeleton", new Color[]{new Color(1315860), new Color(4672845)});
        put("wolf", new Color[]{new Color(14144467), new Color(13545366)});
        put("zoglin", new Color[]{new Color(13004373), new Color(15132390)});
        put("zombie", new Color[]{new Color(44975), new Color(7969893)});
        put("zombie_horse", new Color[]{new Color(3232308), new Color(9945732)});
        put("zombie_villager", new Color[]{new Color(5651507), new Color(7969893)});
        put("zombified_piglin", new Color[]{new Color(15373203), new Color(5009705)});
    }};
    public static final HashMap<Integer, Integer> ARMOR_TRIM_BINDING = new HashMap<>() {{
        put(-2039584, 0);
        put(-4144960, 1);
        put(-6250336, 2);
        put(-8355712, 3);
        put(-10461088, 4);
        put(-12566464, 5);
        put(-14671840, 6);
        put(-16777216, 7);
    }};
    public static final HashMap<String, int[]> ARMOR_TRIM_COLOR = new HashMap<>() {{
        put("amethyst", new int[]{-3567629, -6660922, -9680470, -11389305, -12441738, -13230998, -14414765, -15268293});
        put("copper", new int[]{-1867156, -4954035, -6666452, -8831960, -9620448, -10540264, -11788272, -12773365});
        put("diamond", new int[]{-3407883, -9507630, -13845848, -14838118, -15959923, -16292488, -16494241, -16696249});
        put("diamond_darker", new int[]{-15354975, -16081759, -16481915, -16616334, -16360101, -16166584, -16562622, -16498626});
        put("emerald", new int[]{-8194387, -15808684, -15622090, -15697116, -15830494, -16162021, -16560109, -16630514});
        put("emerald_darker", new int[]{-873458, -873458, -1520311, -792927, -2296927, -6232899, -10300196, -14104842});
        put("gold", new int[]{-624, -1255105, -2182867, -5150958, -6273782, -8375037, -9360128, -11066624});
        put("gold_darker", new int[]{-4023254, -4553945, -6070508, -7780596, -8375037, -9360128, -11066624, -12707069});
        put("iron", new int[]{-3812652, -4208184, -6444374, -8681079, -9339523, -10129296, -11050141, -12168879});
        put("iron_darker", new int[]{-6115149, -7695727, -9472394, -11050141, -12760759, -13550789, -13945804, -14866392});
        put("lapis", new int[]{-12489065, -14922340, -14595717, -15584411, -15651229, -15980454, -16179643, -16443850});
        put("netherite", new int[]{-10856614, -12305861, -13554127, -13687001, -14475746, -15067626, -15725556, -16185593});
        put("netherite_darker", new int[]{-13752279, -14146523, -14148323, -14411238, -14739689, -14871789, -15462641, -16054007});
        put("quartz", new int[]{-856083, -595233, -1844284, -4805226, -7303552, -10133162, -12238020, -14014430});
        put("redstone", new int[]{-1695736, -4382712, -6875641, -8908543, -10155263, -11399930, -13236221, -14875390});
    }};

    private final char colorCode;
    private final Color color;
    private final Color backgroundColor;

}
