package net.hypixel.nerdbot.util;

public class Logger {

    public static void print(String msg) {
        System.out.println(Time.formatNow() + " " + msg);
    }

    public static void info(String msg) {
        print("[INFO] " + msg);
    }

    public static void warning(String msg) {
        print("[WARNING] " + msg);
    }

    public static void error(String msg) {
        print("[ERROR] " + msg);
    }

}
