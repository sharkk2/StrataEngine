package org.sharkk2.sengine;

import org.sharkk2.sengine.core.classes.enums.LogLevel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Logger {
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";


    public record Entry(String time, LogLevel level, String caller, String message) {}

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int HISTORY_LIMIT = 500;
    private static final Deque<Entry> history = new ArrayDeque<>();
    private static long revision = 0;
    private static LogLevel minimumLevel = LogLevel.INFO;

    public static void setMinimumLevel(LogLevel level) {
        minimumLevel = level;
    }

    public static synchronized List<Entry> getHistory() {
        return new ArrayList<>(history);
    }

    public static synchronized long getRevision() {
        return revision;
    }

    public static synchronized void clearHistory() {
        history.clear();
        revision++;
    }

    private static synchronized void record(Entry entry) {
        history.addLast(entry);
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
        revision++;
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public static void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message + " | " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    public static String getCallerClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.equals(Logger.class.getName()) && !className.equals(Thread.class.getName())) {
                return className.substring(className.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }

    private static String getLevelColor(LogLevel level) {
        return switch (level) {
            case INFO -> GREEN;
            case WARNING -> YELLOW;
            case ERROR -> RED;
        };
    }

    private static void log(LogLevel level, String message) {
        if (level.ordinal() < minimumLevel.ordinal()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String caller = getCallerClassName();
        record(new Entry(timestamp, level, caller, message));
        if (level == LogLevel.WARNING) message = YELLOW + message;
        String output = GRAY + "[" + timestamp + "] " + RESET + CYAN + "[" + caller + "] " + RESET +
                getLevelColor(level) + "[" + level + "] " + RESET + message;

        if (level == LogLevel.ERROR) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
    }
}