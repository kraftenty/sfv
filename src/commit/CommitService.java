package commit;

import util.FileUtil;
import util.HashUtil;

import java.io.*;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CommitService {

    public void commit(String message) throws IOException, NoSuchAlgorithmException {
        FileUtil.validateSfvRepository();

        // 1. 현재 작업 디렉토리의 모든 파일 목록 가져오기
        Set<Path> currentFiles = Files.walk(FileUtil.getRootPath())
                .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.toString().contains("/out"))
                .filter(Files::isRegularFile)
                .collect(Collectors.toSet());

        // 2. 변경된 파일 찾기
        List<Path> modifiedFiles = ModifyDetector.findModifiedFiles();
        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }


        // 3. 커밋 객체 생성 및 저장 (현재 존재하는 파일만 포함)
        Map<String, String> newFileMetadata = getFileMetadataV4(currentFiles); // TODO

        Commit commit = new Commit(generateCommitId(message), message, FileUtil.getHEADValue(), newFileMetadata);
        saveCommitToCommitDirectory(commit);

        // 4. HEAD 업데이트
        FileUtil.updateHEADValue(commit.getId());
        System.out.println("[commit] commited " + commit.getId().substring(0, 7) + " (" + message + ")");
    }

    // V1 : 싱글스레드
    private static Map<String, String> getFileMetadataV1(Set<Path> currentFiles) throws IOException, NoSuchAlgorithmException {
        Map<String, String> newFileMetadata = new HashMap<>();
        for (Path file : currentFiles) {
            String normalizedPath = FileUtil.getRootPath().relativize(file).normalize().toString();
            long fileSize = FileUtil.getFileSize(file);
            long lastModifiedTime = FileUtil.getLastModifiedTime(file);
            String hash = FileUtil.calculateFileHash(file); // 좀 오래걸림
            String fileInfo = fileSize + "," + lastModifiedTime + "," + hash;
            newFileMetadata.put(normalizedPath, fileInfo);
        }
        return newFileMetadata;
    }

    // V2 : parallelStream
    private static Map<String, String> getFileMetadataV2(Set<Path> currentFiles) throws IOException, NoSuchAlgorithmException {
        Map<String, String> newFileMetadata = new ConcurrentHashMap<>();
        currentFiles.parallelStream().forEach(file -> {
            try {
                String normalizedPath = FileUtil.getRootPath().relativize(file).normalize().toString();
                long fileSize = FileUtil.getFileSize(file);
                long lastModifiedTime = FileUtil.getLastModifiedTime(file);
                String hash = FileUtil.calculateFileHash(file);
                FileUtil.saveObject(hash, Files.readAllBytes(file));
                String fileInfo = fileSize + "," + lastModifiedTime + "," + hash;
                newFileMetadata.put(normalizedPath, fileInfo);
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });


        return newFileMetadata;
    }



    // V3 : 청크로 나눠서 계산
    private static Map<String, String> getFileMetadataV3(Set<Path> currentFiles) throws IOException {
        Map<String, String> newFileMetadata = new ConcurrentHashMap<>();

        // 1. 스레드 풀 설정
        int threadCount = 32; // TODO
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        // 2. 파일 리스트를 배열로 변환
        List<Path> fileList = new ArrayList<>(currentFiles);
        int totalFiles = fileList.size();
        int filesPerThread = (int) Math.ceil((double) totalFiles / threadCount);

        // 3. 각 스레드에 파일 범위 할당
        for (int i = 0; i < threadCount; i++) {
            int startIndex = i * filesPerThread;
            if (startIndex >= totalFiles) break;

            int endIndex = Math.min(startIndex + filesPerThread, totalFiles);
            List<Path> chunk = fileList.subList(startIndex, endIndex);

            Future<?> future = executor.submit(() -> {
                for (Path file : chunk) {
                    try {
                        String normalizedPath = FileUtil.getRootPath().relativize(file).normalize().toString();
                        long fileSize = FileUtil.getFileSize(file);
                        long lastModifiedTime = FileUtil.getLastModifiedTime(file);
                        String hash = FileUtil.calculateFileHash(file);
                        FileUtil.saveObject(hash, Files.readAllBytes(file));
                        String fileInfo = fileSize + "," + lastModifiedTime + "," + hash;
                        newFileMetadata.put(normalizedPath, fileInfo);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            futures.add(future);
        }

        // 4. 모든 작업 완료 대기
        try {
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

        return newFileMetadata;
    }


    // V4  메서드 : 큰 파일을 따로 떼서 별도의 스레드로 처리
    private static Map<String, String> getFileMetadataV4(Set<Path> currentFiles) throws IOException {
        Map<String, String> newFileMetadata = new ConcurrentHashMap<>();
        
        // 1. 먼저 모든 파일의 크기를 한 번에 수집
        Map<Path, Long> fileSizeCache = currentFiles.parallelStream()
            .collect(Collectors.toConcurrentMap(
                file -> file,
                file -> {
                    try {
                        return FileUtil.getFileSize(file);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            ));

        // 2. 캐시된 크기를 기준으로 정렬
        List<Path> sortedFiles = fileSizeCache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 내림차순
            .map(Map.Entry::getKey)
            .toList();

        int threadCount = 8; // TODO 일반 파일에 할당할 스레드 개수
        int largeFileThreadCount = 3; //TODO 큰 파일에 할당할 스레드 개수
        int normalThreadCount = threadCount - largeFileThreadCount;
        
        ExecutorService largeFileExecutor = Executors.newFixedThreadPool(largeFileThreadCount);
        ExecutorService normalFileExecutor = Executors.newFixedThreadPool(normalThreadCount);
        
        try {
            // 3. 큰 파일 처리 (상위 10%)
            int largeFileCount = Math.max(1, sortedFiles.size() / 20); //TODO
            List<CompletableFuture<Void>> largeFileFutures = new ArrayList<>();
            List<CompletableFuture<Void>> normalFileFutures = new ArrayList<>();
            
            // 큰 파일 처리
            for (int i = 0; i < largeFileCount; i++) {
                Path file = sortedFiles.get(i);
                largeFileFutures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processFile(file, newFileMetadata);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, largeFileExecutor));
            }
            
            // 나머지 파일 처리
            for (int i = largeFileCount; i < sortedFiles.size(); i++) {
                Path file = sortedFiles.get(i);
                normalFileFutures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processFile(file, newFileMetadata);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, normalFileExecutor));
            }
            
            // 모든 작업 대기
            List<CompletableFuture<Void>> allFutures = new ArrayList<>();
            allFutures.addAll(largeFileFutures);
            allFutures.addAll(normalFileFutures);
            CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();
            
        } finally {
            largeFileExecutor.shutdown();
            normalFileExecutor.shutdown();
            try {
                if (!largeFileExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    largeFileExecutor.shutdownNow();
                }
                if (!normalFileExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    normalFileExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return newFileMetadata;
    }

    private static void processFile(Path file, Map<String, String> newFileMetadata) {
        try {
            String normalizedPath = FileUtil.getRootPath().relativize(file).normalize().toString();
            long fileSize = FileUtil.getFileSize(file);
            long lastModifiedTime = FileUtil.getLastModifiedTime(file);
            String hash = FileUtil.calculateFileHash(file);
            FileUtil.saveObject(hash, Files.readAllBytes(file));
            String fileInfo = fileSize + "," + lastModifiedTime + "," + hash;
            newFileMetadata.put(normalizedPath, fileInfo);
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println("Error processing file: " + file);
        }
    }


    public static Commit loadCommitFromCommitDirectory(String commitId) throws IOException {
        try {
            Path commitPath = FileUtil.getCommitPath(commitId);
            if (!Files.exists(commitPath)) {
                throw new IOException("Commit file not exists: " + commitPath);
            }
            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(commitPath));
            Commit commit = (Commit) ois.readObject();
            ois.close();
            return commit;
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load commit: " + commitId, e);
        }
    }

    private static void saveCommitToCommitDirectory(Commit commit) {
        try {
            Path commitPath = FileUtil.getCommitPath(commit.getId());
            ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(commitPath));
            oos.writeObject(commit);
            oos.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // commit id 생성 메서드
    private static String generateCommitId(String message) throws NoSuchAlgorithmException {
        String content = message + LocalDateTime.now().toString();
        return HashUtil.sha1(content.getBytes());
    }

}
