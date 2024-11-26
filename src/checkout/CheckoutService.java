package checkout;

import commit.Commit;
import commit.CommitUtil;
import common.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CheckoutService {

    private final FileUtil fileUtil;
    private final CommitUtil commitUtil;

    public CheckoutService() {
        fileUtil = new FileUtil();
        commitUtil = new CommitUtil();
    }

    public void checkout(String commitId, boolean force) throws IOException {
        fileUtil.validateSfvRepository();

        String headValue = fileUtil.getHEADValue();
        System.out.println("Current HEAD value: " + headValue);

        String fullCommitId = fileUtil.findFullCommitId(commitId);
        System.out.println("Target commit.Commit ID: " + fullCommitId);

        Commit targetCommit = commitUtil.loadCommit(fullCommitId);
        if (targetCommit == null) {
            throw new IOException("Target commit not found: " + commitId);
        }
        System.out.println("Loaded target commit: " + targetCommit.getId());

        // Step 3: 변경된 파일 확인
        if (!force) {
            Commit currentCommit = headValue.isEmpty() ? null : commitUtil.loadCommit(headValue);
            List<Path> modifiedFiles = findModifiedFiles();
            System.out.println("Modified files: " + modifiedFiles);

            if (!modifiedFiles.isEmpty()) {
                throw new IOException("Uncommitted changes detected. Use 'force' to discard changes.");
            }
        }

        // Step 4: 워킹 디렉토리를 목표 커밋 상태로 복원
        System.out.println("Restoring working directory...");
        restoreWorkingDirectory(targetCommit);

        // Step 5: HEAD 업데이트
        fileUtil.updateHEADValue(fullCommitId);
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
}
