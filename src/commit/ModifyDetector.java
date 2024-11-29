package commit;

import util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class ModifyDetector {

    // TODO : 병렬처리 가능

    /**
     * 1. 청크 기반 분할(전체 파일 리스트를 N개의 동일한 청크로 나눠서, 각 스레드가 하나의 청크를 담당)
     * 2. Work Queue 방식 병렬처리(모든 파일을 작업 큐에 넣고, 여러 워커 스레드가 큐에서 작업을 가져가서 처리)
     */

    public static List<Path> findModifiedFiles() throws IOException {
        return doStrategyV3(); // TODO : 여기서 알고리즘 갈아끼우기
    }

    // V1 전략 : 싱글스레드
    private static List<Path> doStrategyV1() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        String head = FileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : CommitService.loadCommitFromCommitDirectory(head);


        Files.walk(FileUtil.getRootPath())
                .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.toString().contains("/out"))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        long currentSize = FileUtil.getFileSize(path);
                        long currentModifiedTime = FileUtil.getLastModifiedTime(path);

                        if (lastCommit != null) {
                            Path relativePath = FileUtil.getRootPath().relativize(path);
                            String normalizedPath = relativePath.normalize().toString();
                            String storedInfo = lastCommit.getFileMetadataMap().get(normalizedPath);

                            if (storedInfo != null) {
                                String[] parts = storedInfo.split(",");
                                long storedSize = Long.parseLong(parts[0]);
                                long storedModifiedTime = Long.parseLong(parts[1]);

                                if (currentSize != storedSize || currentModifiedTime != storedModifiedTime) {
                                    modifiedFiles.add(path);
                                }
                            } else {
                                modifiedFiles.add(path);
                            }
                        } else {
                            modifiedFiles.add(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: Could not access file: " + path);
                    }
                });

        return modifiedFiles;
    }

    // V2 전략 : ParallelStream 사용
    private static List<Path> doStrategyV2() throws IOException {
        List<Path> modifiedFiles = Collections.synchronizedList(new ArrayList<>());
        String head = FileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : CommitService.loadCommitFromCommitDirectory(head);

        Files.walk(FileUtil.getRootPath())
                .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.toString().contains("/out"))
                .filter(Files::isRegularFile)
                .parallel()  // 스트림을 병렬로 처리
                .forEach(path -> {
                    try {
                        long currentSize = FileUtil.getFileSize(path);
                        long currentModifiedTime = FileUtil.getLastModifiedTime(path);

                        if (lastCommit != null) {
                            Path relativePath = FileUtil.getRootPath().relativize(path);
                            String normalizedPath = relativePath.normalize().toString();
                            String storedInfo = lastCommit.getFileMetadataMap().get(normalizedPath);

                            if (storedInfo != null) {
                                String[] parts = storedInfo.split(",");
                                long storedSize = Long.parseLong(parts[0]);
                                long storedModifiedTime = Long.parseLong(parts[1]);

                                if (currentSize != storedSize || currentModifiedTime != storedModifiedTime) {
                                    modifiedFiles.add(path);
                                }
                            } else {
                                modifiedFiles.add(path);
                            }
                        } else {
                            modifiedFiles.add(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: Could not access file: " + path);
                    }
                });

        return modifiedFiles;
    }

    // V3 : 청크로 분할
    private static List<Path> doStrategyV3() throws IOException {
        List<Path> modifiedFiles = Collections.synchronizedList(new ArrayList<>());
        String head = FileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : CommitService.loadCommitFromCommitDirectory(head);

        int threadCount = 4; // 3개가 가장 빠름

        // 2. 작업 큐 사용 - 더 작은 단위로 작업 분할
        BlockingQueue<List<Path>> workQueue = new LinkedBlockingQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        
        // 3. 파일 수집 및 청크 생성을 별도 스레드에서 수행
        Future<?> producerFuture = executor.submit(() -> {
            try {
                int chunkSize = 100; // 더 작은 단위로 분할
                List<Path> currentChunk = new ArrayList<>(chunkSize);
                
                Files.walk(FileUtil.getRootPath())
                    .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                    .filter(path -> !path.toString().contains("/."))
                    .filter(path -> !path.toString().contains("/out"))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        currentChunk.add(path);
                        if (currentChunk.size() >= chunkSize) {
                            workQueue.add(new ArrayList<>(currentChunk));
                            currentChunk.clear();
                        }
                    });
                
                // 마지막 청크 처리
                if (!currentChunk.isEmpty()) {
                    workQueue.add(new ArrayList<>(currentChunk));
                }
                
                // 작업 완료 표시
                for (int i = 0; i < threadCount; i++) {
                    workQueue.add(Collections.emptyList());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // 4. 워커 스레드들이 큐에서 작업을 가져와서 처리
        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    while (true) {
                        List<Path> chunk = workQueue.take();
                        if (chunk.isEmpty()) {
                            break;
                        }
                        
                        for (Path path : chunk) {
                            try {
                                // 파일 메타데이터 캐싱
                                long currentSize = FileUtil.getFileSize(path);
                                long currentModifiedTime = FileUtil.getLastModifiedTime(path);

                                if (lastCommit != null) {
                                    Path relativePath = FileUtil.getRootPath().relativize(path);
                                    String normalizedPath = relativePath.normalize().toString();
                                    String storedInfo = lastCommit.getFileMetadataMap().get(normalizedPath);

                                    if (storedInfo != null) {
                                        String[] parts = storedInfo.split(",");
                                        long storedSize = Long.parseLong(parts[0]);
                                        long storedModifiedTime = Long.parseLong(parts[1]);

                                        if (currentSize != storedSize || currentModifiedTime != storedModifiedTime) {
                                            modifiedFiles.add(path);
                                        }
                                    } else {
                                        modifiedFiles.add(path);
                                    }
                                } else {
                                    modifiedFiles.add(path);
                                }
                            } catch (IOException e) {
                                System.err.println("Warning: Could not access file: " + path);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // 5. 모든 작업 완료 대기
        try {
            producerFuture.get(); // 파일 수집 작업 완료 대기
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Error processing files", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        return modifiedFiles;
    }

}
