package common;

import commit.Commit;
import commit.CommitService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FileChangeDetector {
    public enum Strategy {
        PARALLEL_CHUNKED,    // 멀티스레드 청크 기반
        STREAM_BASED,        // 스트림 기반
        SINGLE_CHUNKED       // 싱글스레드 청크 기반
    }

    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024; // 1MB

    public static List<Path> findChangedFiles(Strategy strategy) throws IOException {
        // 1. 모든 대상 파일 수집 (공통)
        List<Path> allFiles = collectTargetFiles();

        // 2. 전략에 따라 변경된 파일 찾기
        try {
            switch (strategy) {
                case PARALLEL_CHUNKED:
                    return findWithParallelChunked(allFiles);
                case STREAM_BASED:
                    return findWithStream(allFiles);
                case SINGLE_CHUNKED:
                    return findWithSingleChunked(allFiles);
                default:
                    throw new IllegalArgumentException("Unknown strategy: " + strategy);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Hash calculation failed", e);
        }
    }

    private static List<Path> collectTargetFiles() throws IOException {
        List<Path> allFiles = new ArrayList<>();
        Files.walk(FileUtil.getRootPath())
                .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.startsWith(FileUtil.getRootPath().resolve("out")))
                .filter(Files::isRegularFile)
                .filter(Files::exists)
                .forEach(allFiles::add);
        return allFiles;
    }

    private static List<Path> findWithParallelChunked(List<Path> allFiles) 
            throws IOException, NoSuchAlgorithmException {
        List<Path> modifiedFiles = new ArrayList<>();
        ParallelChunkedCalculator calculator = new ParallelChunkedCalculator(
            Runtime.getRuntime().availableProcessors()
        );

        try {
            for (Path path : allFiles) {
                String currentHash = calculator.calculateFileHash(path);
                if (isFileModified(path, currentHash)) {
                    modifiedFiles.add(path);
                }
            }
        } finally {
            calculator.shutdown();
        }
        return modifiedFiles;
    }

    private static List<Path> findWithStream(List<Path> allFiles)
            throws IOException, NoSuchAlgorithmException {
        List<Path> modifiedFiles = new ArrayList<>();

        allFiles.forEach(path -> {
            try {
                String currentHash = FileUtil.calculateFileHash(path);
                if (isFileModified(path, currentHash)) {
                    modifiedFiles.add(path);
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("Warning: Could not access file: " + path);
            }
        });
        return modifiedFiles;
    }

    private static List<Path> findWithSingleChunked(List<Path> allFiles)
            throws IOException, NoSuchAlgorithmException {
        List<Path> modifiedFiles = new ArrayList<>();
        SingleChunkedCalculator hashCalculator = new SingleChunkedCalculator();

        for (Path path : allFiles) {
            try {
                String currentHash = hashCalculator.calculateFileHash(path);
                if (isFileModified(path, currentHash)) {
                    modifiedFiles.add(path);
                }
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Warning: Hash calculation failed for file: " + path);
            }
        }
        return modifiedFiles;
    }

    private static boolean isFileModified(Path path, String currentHash) throws IOException {
        String headValue = FileUtil.getHEADValue();
        Commit lastCommit = headValue.isEmpty() ? null : CommitService.loadCommit(headValue);
        
        if (lastCommit == null) {
            return true; // 첫 커밋인 경우 모든 파일이 수정된 것으로 간주
        }

        Path relativePath = FileUtil.getRootPath().relativize(path);
        String normalizedPath = relativePath.normalize().toString();
        String storedHash = lastCommit.getFileHashes().get(normalizedPath);
        
        if (storedHash == null) {
            return true; // 새로운 파일인 경우
        }
        
        return !currentHash.equals(storedHash);
    }

    // V2 병렬 청크 계산기
    private static class ParallelChunkedCalculator {
        private final ExecutorService executor;
        private final int chunkSize;

        ParallelChunkedCalculator(int nThreads) {
            this.executor = Executors.newFixedThreadPool(nThreads);
            this.chunkSize = DEFAULT_CHUNK_SIZE;
        }

        String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                long fileSize = channel.size();
                int numChunks = (int) Math.ceil((double) fileSize / chunkSize);
                List<Future<String>> chunkHashes = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(numChunks);

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

                latch.await();
                return combineHashes(chunkHashes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Hash calculation interrupted", e);
            }
        }

        private String calculateChunkHash(FileChannel channel, long position, int size)
                throws IOException, NoSuchAlgorithmException {
            ByteBuffer buffer = ByteBuffer.allocate(size);
            synchronized (channel) {
                channel.read(buffer, position);
            }

            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            return HashUtil.sha1(bytes);
        }

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

        void shutdown() {
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

    // V1 : 싱글 청크 계산기
    private static class SingleChunkedCalculator {
        private final int chunkSize;

        SingleChunkedCalculator() {
            this.chunkSize = DEFAULT_CHUNK_SIZE;
        }

        String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                long fileSize = channel.size();
                int numChunks = (int) Math.ceil((double) fileSize / chunkSize);
                List<String> chunkHashes = new ArrayList<>();

                for (int i = 0; i < numChunks; i++) {
                    final long position = (long) i * chunkSize;
                    final int size = (int) Math.min(chunkSize, fileSize - position);
                    String hash = calculateChunkHash(channel, position, size);
                    chunkHashes.add(hash);
                }

                return combineHashes(chunkHashes);
            }
        }

        private String calculateChunkHash(FileChannel channel, long position, int size)
                throws IOException, NoSuchAlgorithmException {
            ByteBuffer buffer = ByteBuffer.allocate(size);
            channel.read(buffer, position);

            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            return HashUtil.sha1(bytes);
        }

        private String combineHashes(List<String> chunkHashes)
                throws NoSuchAlgorithmException {
            StringBuilder combined = new StringBuilder();
            for (String hash : chunkHashes) {
                combined.append(hash);
            }
            return HashUtil.sha1(combined.toString().getBytes());
        }
    }
}
