import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.HashMap;

/**
 * init, commit, checkout, status, log
 * 5개의 핵심 기능을 담당하는 클래스
 */
public class Core {

    private FileUtil fileUtil;
    private CommitUtil commitUtil;

    public Core() {
        this.fileUtil = new FileUtil();
        this.commitUtil = new CommitUtil();
    }

    // Init
    public void init() throws IOException {
        fileUtil.initializeDotSfvDirectory();
    }

    // Commit
    public String commit(String message) throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();
        
        // 1. 변경된 파일 찾기
        long s11 = System.currentTimeMillis();
        List<Path> modifiedFiles = findModifiedFiles();
        long s12 = System.currentTimeMillis();
        System.out.println("findModifiedFiles() : " + (s12 - s11) + "ms");
        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }


        // 2. 변경된 파일들의 해시 계산 (병렬 처리) - ParallelStream : 자동 병렬처리. 수동 병렬처리 연구 해보셈 TODO
        /**
         * 1. ThreadPoolExecutor 사용
         * 2. 생산자-소비자 패턴 구현 (생산자 큐, 소비자 큐)
         */
        Map<Path, String> fileHashes = new ConcurrentHashMap<>();
        long s21 = System.currentTimeMillis();
        modifiedFiles.parallelStream().forEach(file -> {
            try {
                String hash = fileUtil.calculateFileHash(file);
                fileHashes.put(file, hash);
                fileUtil.saveObject(hash, Files.readAllBytes(file));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });
        long s22 = System.currentTimeMillis();
        System.out.println("해시계산() : " + (s22 - s21) + "ms");


        // 3. 커밋 객체 생성 및 저장
        Commit commit = commitUtil.createCommit(message, fileUtil.getHEADValue(), fileHashes);
        // 4. HEAD 업데이트
        fileUtil.updateHEADValue(commit.getId());
        return commit.getId();
    }


    // Checkout
    public void checkout(String commitId) throws IOException {
        fileUtil.validateSfvRepository();

        // 1. 커밋 객체 로드
        Commit targetCommit = commitUtil.loadCommit(commitId);
        if (targetCommit == null) {
            throw new FileSystemException("Commit not found: " + commitId);
        }

        // 2. 현재 작업 디렉토리의 변경사항 확인
        List<Path> modifiedFiles = findModifiedFiles();
        if (!modifiedFiles.isEmpty()) {
            throw new FileSystemException("You have changes after commit. Please commit them first.");
        }

        // 3. 현재 커밋의 파일 정보 가져오기
        String currentCommitId = fileUtil.getHEADValue();
        Map<String, String> currentFiles = new HashMap<>();
        if (!currentCommitId.isEmpty()) {
            Commit currentCommit = commitUtil.loadCommit(currentCommitId);
            currentFiles = currentCommit.getFileHashes();
        }

        // 4. 파일 시스템 업데이트 (삭제/복원)
        Set<String> filesToDelete = new HashSet<>(currentFiles.keySet());
        filesToDelete.removeAll(targetCommit.getFileHashes().keySet());

        // 삭제할 파일 처리
        for (String relativePath : filesToDelete) {
            Files.delete(fileUtil.getRootPath().resolve(relativePath));
        }

        // 복원/업데이트할 파일 처리
        for (Map.Entry<String, String> entry : targetCommit.getFileHashes().entrySet()) {
            String relativePath = entry.getKey();
            String hash = entry.getValue().split(",")[2];
            
            // 현재 파일의 해시값과 다른 경우에만 복사
            if (!hash.equals(currentFiles.get(relativePath))) {
                Path filePath = fileUtil.getRootPath().resolve(relativePath);
                Files.createDirectories(filePath.getParent());
                System.out.println("hash : " + hash);
                System.out.println("fileUtil.getObjectPath(hash) : " + fileUtil.getObjectPath(hash));
                System.out.println("filePath : " + filePath);
                Files.copy(fileUtil.getObjectPath(hash), filePath, StandardCopyOption.REPLACE_EXISTING); // IOExcept
            }
        }

        // 5. 빈 디렉토리 정리
        cleanEmptyDirectories(fileUtil.getRootPath());

        // 6. HEAD 업데이트
        fileUtil.updateHEADValue(commitId);
    }

    // 빈 디렉토리를 정리하는 헬퍼 메서드
    private void cleanEmptyDirectories(Path directory) throws IOException {
        if (directory.equals(fileUtil.getDotSfvPath())) {
            return;  // .sfv 디렉토리는 건드리지 않음
        }

        Files.walk(directory)
            .sorted(Comparator.reverseOrder())  // 하위 디렉토리부터 처리하기 위해 역순 정렬
            .filter(Files::isDirectory)
            .filter(path -> !path.equals(directory))  // 루트 디렉토리는 제외
            .filter(path -> !path.equals(fileUtil.getDotSfvPath()))  // .sfv 디렉토리는 제외
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

    // Status
    public void getStatus() throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();

        // 1. 현재 커밋 정보 가져오기
        String head = fileUtil.getHEADValue();
        Commit currentCommit = head.isEmpty() ? null : commitUtil.loadCommit(head);

        // 2. 변경된 파일 찾기
        List<Path> modifiedFiles = findModifiedFiles();

        // 3. 상태 출력
        System.out.println("status for commit: " + (currentCommit == null ? "none" :
                currentCommit.getId().substring(0, 7) + " " + currentCommit.getMessage()));

        if (modifiedFiles.isEmpty()) {
            System.out.println("No changes detected");
        } else {
            System.out.println("new changes:");
            for (Path file : modifiedFiles) {
                // 상대 경로로 변환하여 출력
                Path relativePath = fileUtil.getRootPath().relativize(file);
                System.out.println("\tmodified: " + relativePath);
            }
        }
    }

    // Log
    public void getLog() throws IOException {
        fileUtil.validateSfvRepository();

        String currentCommitId = fileUtil.getHEADValue();
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

    // TODO : 병렬처리 가능
    /**
     * 1. 청크 기반 분할(전체 파일 리스트를 N개의 동일한 청크로 나눠서, 각 스레드가 하나의 청크를 담당)
     * 2. Work Queue 방식 병렬처리(모든 파일을 작업 큐에 넣고, 여러 워커 스레드가 큐에서 작업을 가져가서 처리)
     */
    private List<Path> findModifiedFiles() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        String head = fileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : commitUtil.loadCommit(head);

        Files.walk(fileUtil.getRootPath())
            .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
            .filter(path -> !path.toString().contains("/."))
            .filter(path -> !path.toString().contains("/out"))
            .filter(Files::isRegularFile)
            .forEach(path -> {
                try {
                    long currentSize = fileUtil.getFileSize(path);
                    long currentModifiedTime = fileUtil.getLastModifiedTime(path);

                    if (lastCommit != null) {
                        Path relativePath = fileUtil.getRootPath().relativize(path);
                        String normalizedPath = relativePath.normalize().toString();
                        String storedInfo = lastCommit.getFileHashes().get(normalizedPath);

                        if (storedInfo != null) {
                            String[] parts = storedInfo.split(",");
                            long storedSize = Long.parseLong(parts[0]);
                            long storedModifiedTime = Long.parseLong(parts[1]);
                            String storedHash = parts[2];  // 해시값 추출

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

}