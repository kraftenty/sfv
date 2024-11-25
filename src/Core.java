import java.io.File;
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
    public String commit(String message) throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();

        String headValue = head.getValue();
        Commit lastCommit = null;

        if (!headValue.isEmpty()) {
            lastCommit = commitUtil.loadCommit(headValue);
        }

        List<Path> modifiedFiles = findModifiedFiles(lastCommit);

        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }

        Map<Path, String> fileHashes = new ConcurrentHashMap<>();

        modifiedFiles.forEach(file -> {
            try {
                // Calculate hash for each modified file
                String hash = fileUtil.calculateFileHash(file);
                fileHashes.put(file, hash);

                // Save the file content to object storage with the hash as the filename
                fileUtil.saveObject(hash, Files.readAllBytes(file)); // Save file content in object storage
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("Error processing file: " + file + " - " + e.getMessage());
            }
        });

        Commit commit = commitUtil.createCommit(message, headValue, fileHashes);

        String commitId = commit.getId();
        commitUtil.saveCommit(commit);

        head.setValue(commitId);

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
    private List<Path> findModifiedFiles(Commit lastCommit) throws IOException {
        return Files.walk(fileUtil.getRootPath())
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().contains(".sfv")) // .sfv 제외
                .filter(path -> !path.toString().contains(".git")) // .git 제외
                .map(path -> {
                    try {
                        String currentHash = HashUtil.sha1FromFile(path);
                        String storedHash = lastCommit == null ? null : lastCommit.getFileHashes().get(path.toString());
                        return !Objects.equals(currentHash, storedHash) ? path : null;
                    } catch (IOException | NoSuchAlgorithmException e) {
                        System.err.println("파일 처리 중 오류: " + path + " - " + e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }




}
