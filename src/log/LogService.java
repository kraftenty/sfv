package log;

import commit.Commit;
import commit.CommitService;
import util.FileUtil;

import java.io.IOException;

public class LogService {

    public void getLog() throws IOException {
        FileUtil.validateSfvRepository();

        String currentCommitId = FileUtil.getHEADValue();
        if (currentCommitId.isEmpty()) {
            System.out.println("No commits yet");
            return;
        }

        // 커밋 히스토리 순회하며 출력
        Commit currentCommit = CommitService.loadCommitFromCommitDirectory(currentCommitId);
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
            currentCommit = CommitService.loadCommitFromCommitDirectory(previousCommitId);
        }
    }
}
