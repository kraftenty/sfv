package status;

import commit.Commit;
import commit.CommitService;
import common.FileChangeDetector;
import common.FileUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class StatusService {

    private final CommitService commitService;

    public StatusService() {
        this.commitService = new CommitService();
    }

    // Status
    public void getStatus() throws IOException, NoSuchAlgorithmException {
        FileUtil.validateSfvRepository();

        // HEAD에서 현재 커밋 정보 가져오기
        String head = FileUtil.getHEADValue(); // HEAD 값을 한 번만 읽음
        Commit currentCommit = head.isEmpty() ? null : commitService.loadCommit(head);

        // 변경된 파일 찾기
        List<Path> modifiedFiles = FileChangeDetector.findChangedFiles(FileChangeDetector.Strategy.STREAM_BASED);

        // 현재 상태 출력
        System.out.println("status for commit: " + (currentCommit == null ? "none" :
                currentCommit.getId().substring(0, 7) + " " + currentCommit.getMessage()));

        if (modifiedFiles.isEmpty()) {
            System.out.println("No changes detected");
        } else {
            System.out.println("new changes:");
            for (Path file : modifiedFiles) {
                // 상대 경로로 변환하여 출력
                Path relativePath = FileUtil.getRootPath().relativize(file);
                System.out.println("\tmodified: " + relativePath);
            }
        }
    }

}
