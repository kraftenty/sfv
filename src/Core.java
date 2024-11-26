
import java.io.File;

import timer.PerformanceTimer;
import java.io.IOException;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Core {

    private final FileUtil fileUtil;
    private final CommitUtil commitUtil;
    private final Head head;

    public Core() {
        this.fileUtil = new FileUtil();
        this.commitUtil = new CommitUtil();
        this.head = new Head(fileUtil.getDotSfvPath());
    }

    // Init
    public void init() throws IOException {
        fileUtil.initializeDotSfvDirectory();
        head.initialize();
    }

     // Commit
     public String commit(String message) throws IOException, NoSuchAlgorithmException {
         fileUtil.validateSfvRepository();
        
         // 1. 변경된 파일 찾기
         PerformanceTimer.start("find-modified-files");
         List<Path> modifiedFiles = findModifiedFiles3();
         PerformanceTimer.stop("find-modified-files");
         System.out.println("\nfind-modified-files Performance:");
         PerformanceTimer.printStats("find-modified-files");
         if (modifiedFiles.isEmpty()) {
             throw new FileSystemException("Nothing to commit.");
         }

//         PerformanceTimer.start("hash-calculation");
//         // 2. 변경된 파일들의 해시 계산 (병렬 처리) - ParallelStream : 자동 병렬처리. 수동 병렬처리 연구 해보셈 TODO
//         Map<Path, String> fileHashes = new ConcurrentHashMap<>();
//         modifiedFiles.parallelStream().forEach(file -> {
//             try {
//                 String hash = fileUtil.calculateFileHash(file);
//                 fileHashes.put(file, hash);
//                 fileUtil.saveObject(hash, Files.readAllBytes(file));
//             } catch (IOException | NoSuchAlgorithmException e) {
//                 throw new RuntimeException(e);
//             }
//         });
//         PerformanceTimer.stop("hash-calculation");
//         System.out.println("\nHash Calculation Performance:");
//         PerformanceTimer.printStats("hash-calculation");
        //2. 병렬 처리를 위한 executor 생성 및 해시 계산  //
        PerformanceTimer.start("hash-calculation");
        FileHashExecutor executor = new FileHashExecutor(Runtime.getRuntime().availableProcessors());
        Map<Path, String> fileHashes = executor.calculateHashes(modifiedFiles);
        executor.shutdown();
        PerformanceTimer.stop("hash-calculation");

        // 성능 통계 출력
        System.out.println("\nHash Calculation Performance:");
        PerformanceTimer.printStats("hash-calculation");
        

         // 3. 커밋 객체 생성 및 저장
         Commit commit = commitUtil.createCommit(message, fileUtil.getHEADValue(), fileHashes);

         // 4. HEAD 업데이트
         fileUtil.updateHEADValue(commit.getId());

         return commit.getId();
     }

        return commitId;
    }
    public void checkout(String commitId, boolean force) throws IOException {
        // Step 1: .sfv 저장소 유효성 검사
        fileUtil.validateSfvRepository();

        // Step 2: HEAD 값 확인 및 전체 커밋 ID 로드
        String headValue = head.getValue();
        System.out.println("Current HEAD value: " + headValue);

        String fullCommitId = fileUtil.findFullCommitId(commitId);
        System.out.println("Target Commit ID: " + fullCommitId);

        Commit targetCommit = commitUtil.loadCommit(fullCommitId);
        if (targetCommit == null) {
            throw new IOException("Target commit not found: " + commitId);
        }
        System.out.println("Loaded target commit: " + targetCommit.getId());

        // Step 3: 변경된 파일 확인
        if (!force) {
            Commit currentCommit = headValue.isEmpty() ? null : commitUtil.loadCommit(headValue);
            List<Path> modifiedFiles = findModifiedFiles(currentCommit);
            System.out.println("Modified files: " + modifiedFiles);

            if (!modifiedFiles.isEmpty()) {
                throw new IOException("Uncommitted changes detected. Use 'force' to discard changes.");
            }
        }

        // Step 4: 워킹 디렉토리를 목표 커밋 상태로 복원
        System.out.println("Restoring working directory...");
        restoreWorkingDirectory(targetCommit);

        // Step 5: HEAD 업데이트
        head.setValue(fullCommitId);
        System.out.println("HEAD updated to commit: " + fullCommitId.substring(0, 7));
    }

    // 디버깅 추가: restoreWorkingDirectory
    private void restoreWorkingDirectory(Commit targetCommit) throws IOException {
        Map<String, String> targetFileHashes = targetCommit.getFileHashes();

        // Step 1: 모든 현재 파일 찾기
        List<Path> allFiles = Files.walk(fileUtil.getRootPath())
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().contains(".sfv")) // .sfv 디렉토리 제외
                .filter(path -> !path.toString().contains(".git")) // .git 디렉토리 제외
                .collect(Collectors.toList());

        // Step 2: 커밋에 포함되지 않은 파일 삭제 (병렬 처리)
        List<Path> filesToDelete = allFiles.stream()
                .filter(file -> {
                    String relativePath = fileUtil.getRootPath().relativize(file).toString();
                    return !targetFileHashes.containsKey(relativePath);
                })
                .collect(Collectors.toList());

        filesToDelete.parallelStream().forEach(file -> {
            try {
                Files.delete(file);
                System.out.println("Deleted: " + file);
            } catch (IOException e) {
                // 예외 처리: 로깅 또는 재시도 로직 추가 가능
                System.err.println("Failed to delete: " + file + " - " + e.getMessage());
            }
        });

        // Step 3: 커밋 파일 복원 (병렬 처리)
        targetFileHashes.entrySet().parallelStream().forEach(entry -> {
            String relativePath = entry.getKey();
            String hash = entry.getValue();
            String rootPathStr = fileUtil.getRootPath().toString();
            Path fullPath = Paths.get(rootPathStr, relativePath);

            try {
                System.out.println("Restoring file: " + relativePath);
                System.out.println("Target absolute path: " + fullPath);

                Path objectPath = fileUtil.getObjectPath(hash);
                if (Files.exists(objectPath)) {
                    Files.createDirectories(fullPath.getParent()); // 상위 디렉토리 생성
                    Files.write(fullPath, Files.readAllBytes(objectPath)); // 파일 복원
                    System.out.println("Restored: " + fullPath);
                } else {
                    throw new IOException("Missing object file for hash: " + hash);
                }
            } catch (IOException e) {
                // 예외 처리: 로깅 또는 재시도 로직 추가 가능
                System.err.println("Failed to restore file: " + relativePath + " - " + e.getMessage());
            }
        });

        System.out.println("Working directory restored to commit: " + targetCommit.getId());
    }





    // Status
    public void getStatus() throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();

        // HEAD에서 현재 커밋 정보 가져오기
        String headValue = head.getValue(); // HEAD 값을 한 번만 읽음
        Commit currentCommit = headValue.isEmpty() ? null : commitUtil.loadCommit(headValue);

        // 변경된 파일 찾기
        List<Path> modifiedFiles = findModifiedFiles(currentCommit); // 커밋 객체를 전달

        // 현재 상태 출력
        System.out.println("status for commit: " + (currentCommit == null ? "none" :
                currentCommit.getId().substring(0, 7) + " " + currentCommit.getMessage()));

        // 변경된 파일 출력
        if (modifiedFiles.isEmpty()) {
            System.out.println("No changes detected");
        } else {
            System.out.println("new changes:");
            modifiedFiles.parallelStream()
                    .forEach(file -> {
                        Path relativePath = fileUtil.getRootPath().relativize(file);
                        System.out.println("\tmodified: " + relativePath);
                    });
        }
    }


    // Log
    public void getLog() throws IOException {
        fileUtil.validateSfvRepository();

        String currentCommitId = head.getValue();
        if (currentCommitId.isEmpty()) {
            System.out.println("No commits yet");
            return;
        }

        // 커밋 히스토리 순회하며 출력
        Commit currentCommit = commitUtil.loadCommit(currentCommitId);
        while (currentCommit != null) {
            System.out.println("commit " + currentCommit.getId());
            System.out.println("Date: " + currentCommit.getTimestamp());
            System.out.println("message: " + currentCommit.getMessage());
            System.out.println("-----------------------------------------------");

            // 이전 커밋으로 이동
            String previousCommitId = currentCommit.getPreviousCommitId();
            if (previousCommitId.isEmpty()) {
                break;
            }
            currentCommit = commitUtil.loadCommit(previousCommitId);
        }
    }


    // TODO : 병렬 처리 적용 가능
    private List<Path> findModifiedFiles() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        List<Path> allFiles = new ArrayList<>();
        
        // 1. 모든 대상 파일 수집
        Files.walk(fileUtil.getRootPath())
            .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
            .filter(path -> !path.toString().contains("/."))
            .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))
            .filter(Files::isRegularFile)
            .filter(Files::exists)
            .forEach(allFiles::add);

        // 2. ChunkedHashCalculator 초기화
        ChunkedHashCalculator hashCalculator = new ChunkedHashCalculator(
            Runtime.getRuntime().availableProcessors() //cpu 코어 수 만큼 스레드 할당
        );

        try {
            // 3. HEAD 값과 마지막 커밋 가져오기
            String head = fileUtil.getHEADValue();
            Commit lastCommit = head.isEmpty() ? null : commitUtil.loadCommit(head);

            // 4. 각 파일에 대해 청크 기반 해시 계산 및 비교
            for (Path path : allFiles) {
                try {
                    String currentHash = hashCalculator.calculateFileHash(path);
                    String storedHash = "__firsthash__";

                    if (lastCommit != null) {
                        Path relativePath = fileUtil.getRootPath().relativize(path);
                        String normalizedPath = relativePath.normalize().toString();
                        storedHash = lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
                    }

                    if (!currentHash.equals(storedHash)) {
                        modifiedFiles.add(path);
                    }
                } catch (NoSuchAlgorithmException e) {
                    System.err.println("Warning: Hash calculation failed for file: " + path);
                }
            }
        } finally {
            // 5. 리소스 정리
            hashCalculator.shutdown();
        }

        return modifiedFiles;
    }

     // TODO : 병렬 처리 적용 가능
     private List<Path> findModifiedFiles2() throws IOException {
         List<Path> modifiedFiles = new ArrayList<>();
         Files.walk(fileUtil.getRootPath())
             .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
             .filter(path -> !path.toString().contains("/."))  // .으로 시작하는 모든 디렉토리 제외
             .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))  // out 디렉토리 제외
             .filter(Files::isRegularFile)
             .filter(Files::exists)
             .forEach(path -> {
                 try {
                     String currentHash = fileUtil.calculateFileHash(path);
                    
                     // 저장된 해시값 가져오기
                     String head = fileUtil.getHEADValue();
                     String storedHash = "__firsthash__";
                    
                     if (!head.isEmpty()) {
                         Commit lastCommit = commitUtil.loadCommit(head);
                         if (lastCommit != null) {
                             Path relativePath = fileUtil.getRootPath().relativize(path);
                             String normalizedPath = relativePath.normalize().toString();
                             storedHash = lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
                         }
                     }
                    
 //                    System.out.println("currentHash = " + currentHash + ", storedHash = " + storedHash);
                     if (!currentHash.equals(storedHash)) {
                         modifiedFiles.add(path);
                     }
                 } catch (IOException | NoSuchAlgorithmException e) {
                     System.err.println("Warning: Could not access file: " + path);
                 }
             });
         return modifiedFiles;
     }

    private List<Path> findModifiedFiles3() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        List<Path> allFiles = new ArrayList<>();
        
        // 1. 모든 대상 파일 수집
        Files.walk(fileUtil.getRootPath())
            .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
            .filter(path -> !path.toString().contains("/."))
            .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))
            .filter(Files::isRegularFile)
            .filter(Files::exists)
            .forEach(allFiles::add);

        // 2. ChunkedHashCalculator2 초기화 (싱글스레드)
        ChunkedHashCalculator2 hashCalculator = new ChunkedHashCalculator2();

        // 3. HEAD 값과 마지막 커밋 가져오기
        String head = fileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : commitUtil.loadCommit(head);

        // 4. 각 파일에 대해 청크 기반 해시 계산 및 비교
        for (Path path : allFiles) {
            try {
                String currentHash = hashCalculator.calculateFileHash(path);
                String storedHash = "__firsthash__";

                if (lastCommit != null) {
                    Path relativePath = fileUtil.getRootPath().relativize(path);
                    String normalizedPath = relativePath.normalize().toString();
                    storedHash = lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
                }

                if (!currentHash.equals(storedHash)) {
                    modifiedFiles.add(path);
                }
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Warning: Hash calculation failed for file: " + path);
            }
        }

        return modifiedFiles;
    }




}
