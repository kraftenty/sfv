package status;

import commit.Commit;
import commit.CommitService;
import commit.ModifyDetector;
import util.FileUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class StatusService {

    public void getStatus() throws IOException, NoSuchAlgorithmException {
        FileUtil.validateSfvRepository();

        // 1. 현재 커밋 정보 가져오기
        String head = FileUtil.getHEADValue();
        Commit currentCommit = head.isEmpty() ? null : CommitService.loadCommitFromCommitDirectory(head);

        // 2. 변경된 파일 찾기
        List<Path> modifiedFiles = ModifyDetector.findModifiedFiles();

        // 3. 상태 출력
        System.out.println("[status] status for commit: " + (currentCommit == null ? "none. this is first commit." :
                currentCommit.getId().substring(0, 7) + " " + currentCommit.getMessage()));

        if (modifiedFiles.isEmpty()) {
            System.out.println("no changes detected");
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
