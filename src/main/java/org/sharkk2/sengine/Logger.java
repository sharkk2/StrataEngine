package org.sharkk2.sengine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";

    public enum Level {
        INFO, WARNING, ERROR
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Level minimumLevel = Level.INFO;

    public static void setMinimumLevel(Level level) {
        minimumLevel = level;
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warning(String message) {
        log(Level.WARNING, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message + " | " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    private static String getCallerClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.equals(Logger.class.getName()) && !className.equals(Thread.class.getName())) {
                return className.substring(className.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }

    private static String getLevelColor(Level level) {
        return switch (level) {
            case INFO -> GREEN;
            case WARNING -> YELLOW;
            case ERROR -> RED;
        };
    }

    private static void log(Level level, String message) {
        if (level.ordinal() < minimumLevel.ordinal()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String caller = getCallerClassName();
        if (level == Level.WARNING) message = YELLOW + message;
        String output = GRAY + "[" + timestamp + "] " + RESET + CYAN + "[" + caller + "] " + RESET +
                        getLevelColor(level) + "[" + level + "] " + RESET + message;

        if (level == Level.ERROR) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
    }
}