import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ChunkedHashCalculator {
    private final ExecutorService executor;
    private final int chunkSize;
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024; // 1MB

    public ChunkedHashCalculator(int nThreads) {
        this.executor = Executors.newFixedThreadPool(nThreads);
        this.chunkSize = DEFAULT_CHUNK_SIZE;
    }
    //파일의 해시를 계산하는 메서드
    public String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            //파일의 크기를 읽어 chunkSize로 나누어 청크의 개수를 계산
            long fileSize = channel.size();
            int numChunks = (int) Math.ceil((double) fileSize / chunkSize);
            List<Future<String>> chunkHashes = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(numChunks);

            // 각 청크에 대한 해시 계산 작업 제출
            for (int i = 0; i < numChunks; i++) {
                final long position = (long) i * chunkSize;
                final int size = (int) Math.min(chunkSize, fileSize - position);
                
                Future<String> future = executor.submit(() -> {
                    try {
                        return calculateChunkHash(channel, position, size);
                    } finally {
                        latch.countDown();
                    }
                });
                chunkHashes.add(future);
            }

            // 모든 청크의 해시 계산이 완료될 때까지 대기
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Hash calculation interrupted", e);
            }

            // 모든 청크의 해시값을 결합하여 최종 해시 계산
            return combineHashes(chunkHashes);
        }
    }
    //청크의 해시를 계산하는 메서드
    private String calculateChunkHash(FileChannel channel, long position, int size) 
            throws IOException, NoSuchAlgorithmException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        //채널을 동기화하여 청크를 읽음
        synchronized (channel) {
            channel.read(buffer, position);
        }
        
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        
        return HashUtil.sha1(bytes);
    }
    //청크의 해시값을 결합하여 최종 해시를 계산하는 메서드
    private String combineHashes(List<Future<String>> chunkHashes) 
            throws IOException, NoSuchAlgorithmException {
        StringBuilder combined = new StringBuilder();
        
        try {
            for (Future<String> future : chunkHashes) {
                combined.append(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Error combining hashes", e);
        }

        return HashUtil.sha1(combined.toString().getBytes());
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