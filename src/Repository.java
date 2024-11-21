import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Repository {

    private final ParallelStrategy parallelStrategy;
    private FileUtil fileUtil;

    public Repository(ParallelStrategy parallelStrategy) {
        this.parallelStrategy = parallelStrategy;
        this.fileUtil = new FileUtil();
    }

    public void init() throws FileSystemException, IOException {
        fileUtil.initializeDotSfvDirectory();
    }

    public String commit(String message) throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();
        // 변경된 파일 리스트 불러오기
        List<Path> modifiedFiles = findModifiedFiles();
        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }
        
        // 변경된 파일들의 체크섬을 병렬로 계산하고 저장
        // TODO
        
        // 새로운 커밋 생성
        Commit commit = createCommit(message, fileUtil.getHEADValue(), modifiedFiles);
        // HEAD 업데이트
        fileUtil.updateHEADValue(commit.getId());
        
        return commit.getId();
    }


    public void checkout(String commitId) {

    }

    public void getStatus() throws FileSystemException {
        fileUtil.validateSfvRepository();
        List<String> changes = new ArrayList<>();


    }

    public void getLog() {

    }


    // private method

    private List<Path> findModifiedFiles() throws IOException {
        // TODO
        return null;
    }


    private Commit createCommit(String message, String HEAD, List<Path> modifiedFiles) throws NoSuchAlgorithmException {
        String commitId = generateCommitId(message);
        // TODO : modifiedFiles를 가지고, 병렬 처리를 통해 계산
        Commit commit = new Commit(commitId, message, HEAD);
        saveCommit(commit);
        return commit;
    }

    private void saveCommit(Commit commit) {
        // TODO : 커밋을 파일로 저장하기
    }

    // commit id 생성 메서드
    private String generateCommitId(String message) throws NoSuchAlgorithmException {
        String input = System.currentTimeMillis() + message;
        MessageDigest digest = null;
        digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(input.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }


}
