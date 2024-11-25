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
            System.out.println("커밋을 로드 중: " + commitPath);

            if (!Files.exists(commitPath)) {
                System.err.println("커밋 파일이 존재하지 않습니다: " + commitPath);
                return null; // 커밋 파일이 없으면 null 반환
            }

            if (Files.size(commitPath) == 0) {
                System.err.println("커밋 파일이 비어 있습니다: " + commitPath);
                return null; // 커밋 파일이 비어 있으면 null 반환
            }

            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(commitPath))) {
                return (Commit) ois.readObject(); // 커밋 객체를 읽어 반환
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("클래스를 찾을 수 없습니다: " + commitId, e);
        } catch (IOException e) {
            throw new IOException("커밋 로드 중 오류 발생: " + commitId, e);
        }
    }



    public void saveCommit(Commit commit) throws IOException {
        Path commitPath = fileUtil.getCommitPath(commit.getId());
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(commitPath))) {
            oos.writeObject(commit); // 커밋 객체를 직렬화하여 파일로 저장
        }
    }


    // commit id 생성 메서드
    private String generateCommitId(String message) throws NoSuchAlgorithmException {
        String content = message + LocalDateTime.now().toString();
        return HashUtil.sha1(content.getBytes());
    }
}
