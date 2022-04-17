package net.hypixel.nerdbot.util;

public class Logger {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public static void print(String msg) {
        System.out.println(ANSI_RESET + Time.formatNow() + " " + msg);
    }

    public static void info(String msg) {
        print("[INFO] " + msg);
    }

    public static void warning(String msg) {
        print(ANSI_YELLOW + "[WARNING] " + msg);
    }

    public static void error(String msg) {
        print(ANSI_RED + "[ERROR] " + msg);
    }

}
