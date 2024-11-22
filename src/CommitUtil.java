import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class CommitUtil {

    private FileUtil fileUtil;

    public CommitUtil() {
        fileUtil = new FileUtil();
    }

    public Commit createCommit(String message, String HEAD, Map<Path, String> fileHashes) throws NoSuchAlgorithmException, IOException {
        // 이전 커밋의 파일 해시 정보를 가져옴
        Map<String, String> fileHashMap = new HashMap<>(); // key : 파일 상대경로, value : 해시값
        if (!HEAD.isEmpty()) {
            Commit previousCommit = loadCommit(HEAD);
            if (previousCommit != null) {
                fileHashMap.putAll(previousCommit.getFileHashes());
            }
        }

        // 새로운 변경사항 추가
        for (Map.Entry<Path, String> entry : fileHashes.entrySet()) {
            Path relativePath = fileUtil.getRootPath().relativize(entry.getKey());
            String normalizedPath = relativePath.normalize().toString();
            fileHashMap.put(normalizedPath, entry.getValue());
        }

        Commit commit = new Commit(generateCommitId(message), message, HEAD, fileHashMap);
        saveCommit(commit);
        return commit;
    }

    public Commit loadCommit(String commitId) throws IOException {
        try {
            Path commitPath = fileUtil.getCommitPath(commitId);
            if (!Files.exists(commitPath)) {
                return null;
            }
            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(commitPath));
            Commit commit = (Commit) ois.readObject();
            ois.close();
            return commit;
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load commit: " + commitId, e);
        }
    }

    private void saveCommit(Commit commit) {
        try {
            Path commitPath = fileUtil.getCommitPath(commit.getId());
            ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(commitPath));
            oos.writeObject(commit);
            oos.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // commit id 생성 메서드
    private String generateCommitId(String message) throws NoSuchAlgorithmException {
        String content = message + LocalDateTime.now().toString();
        return HashUtil.sha1(content.getBytes());
    }
}
