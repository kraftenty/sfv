package checkout;

import commit.CommitService;
import commit.ModifyDetector;
import util.FileUtil;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

public class CheckoutService {

    public void checkout(String partialTargetCommitId) throws IOException, InterruptedException {
        FileUtil.validateSfvRepository();

        // 1. 완전한 커밋 ID 찾기 (커밋ID 를 다 입력하는건 불편하므로, partialCommitId를 입력받은다음 fullCommitId로 변환)
        String targetCommitId = FileUtil.findMatchingCommitId(partialTargetCommitId);
        System.out.println("[checkout] " + FileUtil.getHEADValue() + " -> " + targetCommitId);

        // 2. 현재 작업 디렉토리의 커밋 후 변경사항 확인. 커밋 후 변경사항이 있으면 안됨.
        // 반드시 커밋 후에 체크아웃 요망
        List<Path> modifiedFiles = ModifyDetector.findModifiedFiles();
        if (!modifiedFiles.isEmpty()) {
            throw new FileSystemException("You have changes after commit. Please commit them first.");
        }

        // 3. 현재 커밋, 타겟 커밋의 정보 가져오기
        // key: src/Main.java
        // value: 923,1732756949846,2b1903dbff5764c8a2829759740f8fec68d05c56
        // ...
        Map<String, String> currentCommitMetadataMap = CommitService.loadCommitFromCommitDirectory(FileUtil.getHEADValue()).getFileMetadataMap();
        Map<String, String> targetCommitMetadataMap = CommitService.loadCommitFromCommitDirectory(targetCommitId).getFileMetadataMap();


        // 4-1. 파일 갱신 (삭제)
        Set<String> filesToDelete = new HashSet<>(currentCommitMetadataMap.keySet());
        filesToDelete.removeAll(targetCommitMetadataMap.keySet());
        for (String filePath : filesToDelete) {
            System.out.println("\tdeleting " + filePath);
            Files.delete(FileUtil.getRootPath().resolve(filePath));
        }

        // 4-2. 파일 갱신 (복원, 수정) TODO : 병렬처리 가능 구간
        restoreFileV4(targetCommitMetadataMap, currentCommitMetadataMap); // TODO : 여기서 알고리즘 갈아끼우셈

        // 5. 빈 디렉토리 정리
        cleanEmptyDirectories(FileUtil.getRootPath());

        // 6. HEAD 업데이트
        FileUtil.updateHEADValue(targetCommitId);

        System.out.println("checkout complete.");
    }

    // V1 : 싱글 스레드
    private static void restoreFileV1(Map<String, String> targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException {
        for (Map.Entry<String, String> entry : targetCommitMetadataMap.entrySet()) {
            String targetFilePath = entry.getKey();
            String targetFileHash = entry.getValue().split(",")[2];
            
            // 현재 커밋에 파일이 없거나 해시값이 다른 경우 복원
            if (!currentCommitMetadataMap.containsKey(targetFilePath) || 
                !currentCommitMetadataMap.get(targetFilePath).split(",")[2].equals(targetFileHash)) {
                Path filePath = FileUtil.getRootPath().resolve(targetFilePath);
                Files.createDirectories(filePath.getParent());
                System.out.println("\trestoring " + filePath);
                Files.copy(FileUtil.getObjectPath(targetFileHash), filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // V2 : parallel stream
    private static void restoreFileV2(Map<String, String> targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException {
        targetCommitMetadataMap.entrySet()
                .parallelStream()
                .forEach(entry -> {
                    try {
                        String targetFilePath = entry.getKey();
                        String targetFileHash = entry.getValue().split(",")[2];

                        // 현재 커밋에 파일이 없거나 해시값이 다른 경우 복원
                        if (!currentCommitMetadataMap.containsKey(targetFilePath) ||
                                !currentCommitMetadataMap.get(targetFilePath).split(",")[2].equals(targetFileHash)) {
                            Path filePath = FileUtil.getRootPath().resolve(targetFilePath);
                            Files.createDirectories(filePath.getParent());
                            System.out.println("\trestoring " + filePath);
                            Files.copy(FileUtil.getObjectPath(targetFileHash), filePath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }


    // V3 : 고정 개수 청크 분배
    private static void restoreFileV3(Map<String, String> targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException, InterruptedException {
        int threadCount = 11; // 스레드 개수
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<Map.Entry<String, String>> entries = new ArrayList<>(targetCommitMetadataMap.entrySet());
        int totalSize = entries.size();
        int chunkSize = (int) Math.ceil((double) totalSize / threadCount); // 고정된 청크 크기 계산

        List<Future<?>> futures = new ArrayList<>();

        // 각 스레드에 고정된 작업 범위를 할당
        for (int i = 0; i < threadCount; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, totalSize); // 범위 초과 방지

            // 고정된 범위를 처리하는 작업
            futures.add(executor.submit(() -> {
                for (int j = start; j < end; j++) {
                    Map.Entry<String, String> entry = entries.get(j);
                    String targetFilePath = entry.getKey();
                    String targetFileHash = entry.getValue().split(",")[2];

                    try {
                        // 현재 커밋에 파일이 없거나 해시값이 다른 경우 복원
                        if (!currentCommitMetadataMap.containsKey(targetFilePath) ||
                                !currentCommitMetadataMap.get(targetFilePath).split(",")[2].equals(targetFileHash)) {
                            Path filePath = FileUtil.getRootPath().resolve(targetFilePath);

                            Files.createDirectories(filePath.getParent());
                            System.out.println("\trestoring " + filePath);
                            Files.copy(FileUtil.getObjectPath(targetFileHash), filePath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }));
        }

        // 모든 작업 완료 대기
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new IOException("Error restoring files", e);
            }
        }

        // 스레드 풀 종료
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
    }

    // V4 : 생산자 - 소비자 (청크나눠서. 가변 개수 청크)
    private static void restoreFileV4(Map<String, String> targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException {
        int threadCount = 16; // 스레드 개수.
        BlockingQueue<List<Map.Entry<String, String>>> workQueue = new LinkedBlockingQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        // 1. 생산자: 작업을 청크 단위로 분할하여 큐에 추가
        Future<?> producerFuture = executor.submit(() -> {
            try {
                int chunkSize = 100; // 청크사이즈도 조절해야함. TODO
                List<Map.Entry<String, String>> currentChunk = new ArrayList<>(chunkSize);

                for (Map.Entry<String, String> entry : targetCommitMetadataMap.entrySet()) {
                    currentChunk.add(entry);
                    if (currentChunk.size() >= chunkSize) {
                        workQueue.add(new ArrayList<>(currentChunk));
                        currentChunk.clear();
                    }
                }

                // 마지막 청크 처리
                if (!currentChunk.isEmpty()) {
                    workQueue.add(new ArrayList<>(currentChunk));
                }

                // 작업 완료 표시
                for (int i = 0; i < threadCount; i++) {
                    workQueue.add(Collections.emptyList());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 2. 소비자: 각 워커 스레드가 큐에서 작업을 가져와 처리
        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    while (true) {
                        List<Map.Entry<String, String>> chunk = workQueue.take();
                        if (chunk.isEmpty()) {
                            break;
                        }

                        for (Map.Entry<String, String> entry : chunk) {
                            String targetFilePath = entry.getKey();
                            String targetFileHash = entry.getValue().split(",")[2];

                            // 현재 커밋에 파일이 없거나 해시값이 다른 경우 복원
                            if (!currentCommitMetadataMap.containsKey(targetFilePath) ||
                                    !currentCommitMetadataMap.get(targetFilePath).split(",")[2].equals(targetFileHash)) {
                                Path filePath = FileUtil.getRootPath().resolve(targetFilePath);
                                Files.createDirectories(filePath.getParent());
                                System.out.println("\trestoring " + filePath);
                                Files.copy(FileUtil.getObjectPath(targetFileHash), filePath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // 3. 모든 작업 완료 대기
        try {
            producerFuture.get();
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Error restoring files", e);
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
    }

    // 빈 디렉토리를 정리하는 헬퍼 메서드
    private void cleanEmptyDirectories(Path directory) throws IOException {
        if (directory.equals(FileUtil.getDotSfvPath())) {
            return;  // .sfv 디렉토리는 건드리지 않음
        }

        Files.walk(directory)
                .sorted(Comparator.reverseOrder())  // 하위 디렉토리부터 처리하기 위해 역순 정렬
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(directory))  // 루트 디렉토리는 제외
                .filter(path -> !path.equals(FileUtil.getDotSfvPath()))  // .sfv 디렉토리는 제외
                .forEach(path -> {
                    try {
                        if (Files.list(path).count() == 0) {  // 디렉토리가 비어있는지 확인
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: Could not process directory: " + path);
                    }
                });
    }
}
