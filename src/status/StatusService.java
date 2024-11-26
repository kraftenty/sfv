package status;

import commit.Commit;
import commit.CommitUtil;
import common.FileUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class StatusService {

    private final FileUtil fileUtil;
    private final CommitUtil commitUtil;

    public StatusService() {
        this.fileUtil = new FileUtil();
        this.commitUtil = new CommitUtil();
    }

    // Status
    public void getStatus() throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();

        // HEAD에서 현재 커밋 정보 가져오기
        String headValue = fileUtil.getHEADValue(); // HEAD 값을 한 번만 읽음
        Commit currentCommit = headValue.isEmpty() ? null : commitUtil.loadCommit(headValue);

        // 변경된 파일 찾기
        List<Path> modifiedFiles = findModifiedFiles(); // 커밋 객체를 전달

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

}
