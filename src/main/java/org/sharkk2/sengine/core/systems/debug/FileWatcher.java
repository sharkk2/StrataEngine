package org.sharkk2.sengine.core.systems.debug;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher {
    private final Path file;
    private final Path dir;
    private final Consumer<WatchEvent.Kind<?>> onChange;
    private volatile boolean running = false;
    private long interval = 3;
    private WatchService watchService;
    public static WatchEvent.Kind<Path> MODIFY = ENTRY_MODIFY;
    public static WatchEvent.Kind<Path> CREATE = ENTRY_CREATE;
    public static WatchEvent.Kind<Path> DELETE = ENTRY_DELETE;


    public FileWatcher(Path filePath, Consumer<WatchEvent.Kind<?>> onChange) {
        this.file = filePath;
        this.dir = filePath.getParent() != null ? filePath.getParent() : Path.of(".");
        this.onChange = onChange;
    }

    public void setInterval(long seconds) {this.interval = seconds;}
    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start watcher", e);
        }

        running = true;

        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(interval, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) continue;
                Path changed = (Path) event.context();
                if (changed.getFileName().equals(file.getFileName())) {
                    onChange.accept(kind);
                }
            }

            key.reset();
        }
    }

    public void stop() {
        running = false;
        try {if (watchService != null) watchService.close();}
        catch (Exception nig) {}
    }
}