package org.sharkk2.sengine.core.systems;

import java.util.concurrent.*;

public class ThreadService {
    private final ExecutorService executor;
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private final Thread mainThread;

    public ThreadService() {
        this.executor = Executors.newCachedThreadPool();
        this.mainThread = Thread.currentThread();

    }

    public ThreadService(int poolSize) {
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.mainThread = Thread.currentThread();

    }

    public void runTask(Runnable task) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(task, executor);
    }

    public void runMainThread(Runnable task) {
        if (Thread.currentThread() == mainThread) {
            task.run();
        } else {
            mainThreadTasks.add(task);
        }
    }

    public <T> T runMainThreadBlocking(Callable<T> task) {
        if (Thread.currentThread() == mainThread) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        mainThreadTasks.add(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void drainTasks() {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            task.run();
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}