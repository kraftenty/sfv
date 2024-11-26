import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class FileHashExecutor {
    private final ExecutorService executor;
    private final FileUtil fileUtil;
    
    public FileHashExecutor(int nThreads) {
        this.executor = Executors.newFixedThreadPool(nThreads);
        this.fileUtil = new FileUtil();
    }
    
    public Map<Path, String> calculateHashes(List<Path> files) {
        Map<Path, String> results = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(files.size());
        
        for (Path file : files) {
            executor.submit(() -> {
                try {
                    String hash = fileUtil.calculateFileHash(file);
                    results.put(file, hash);
                    fileUtil.saveObject(hash, Files.readAllBytes(file));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(); // 모든 작업이 완료될 때까지 대기
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