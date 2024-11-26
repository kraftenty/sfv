package commit;

import common.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class FileHashExecutor {
    private final ExecutorService executor;

    public FileHashExecutor(int nThreads) {
        this.executor = Executors.newFixedThreadPool(nThreads);
    }
    
    public Map<Path, String> calculateHashes(List<Path> files) {
        Map<Path, String> results = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(files.size());
        
        for (Path file : files) {
            executor.submit(() -> {
                try {
                    String hash = FileUtil.calculateFileHash(file);
                    Path relativePath = FileUtil.getRootPath().relativize(file);
                    String normalizedPath = relativePath.normalize().toString();
                    results.put(relativePath, hash);
                    FileUtil.saveObject(hash, Files.readAllBytes(file));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return results;
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 