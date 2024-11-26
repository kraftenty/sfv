package common;

import java.nio.file.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class FileUtil {

    private static final String DOT_SFV = ".sfv";
    private static final String COMMIT = "commit";
    private static final String OBJECT = "object";
    private static final String HEAD = "HEAD";

    private final Path rootPath;
    private final Path dotSfvPath;
    private final Path commitsPath;
    private final Path objectsPath;
    private final Path headPath;

    public FileUtil() {
        this.rootPath = Paths.get(".");
        this.dotSfvPath = rootPath.resolve(DOT_SFV);
        this.commitsPath = Paths.get(dotSfvPath.toString(), COMMIT);
        this.objectsPath = Paths.get(dotSfvPath.toString(), OBJECT);
        this.headPath = Paths.get(dotSfvPath.toString(), HEAD);
    }

    public void initializeDotSfv() throws FileSystemException, IOException {
        if (Files.exists(dotSfvPath)) {
            throw new FileSystemException("sfv repository already exists: " + dotSfvPath);
        }

        // .sfv 디렉토리와 하위 디렉토리 생성
        Files.createDirectory(dotSfvPath);
        Files.createDirectory(objectsPath);
        Files.createDirectory(commitsPath);
    }

    public void validateSfvRepository() throws FileSystemException {
        if (!Files.exists(dotSfvPath) || !Files.exists(objectsPath)
                || !Files.exists(commitsPath)) {
            throw new FileSystemException("sfv repository is not initialized");
        }
    }

    public String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(file);
        return HashUtil.sha1(content);
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Path getDotSfvPath() {
        return dotSfvPath;
    }

    public Path getCommitPath(String commitId) {
        if (commitId == null || commitId.isEmpty()) {
            throw new IllegalArgumentException("Invalid commit ID: " + commitId);
        }
        return commitsPath.resolve(commitId); // .sfv/commit/ 경로 반환
    }

    public Path getObjectPath(String hash) {
        if (hash == null || hash.isEmpty()) {
            throw new IllegalArgumentException("Invalid hash: " + hash);
        }
        // 전체 해시를 파일 이름으로 사용
        return objectsPath.resolve(hash);
    }

    public void saveObject(String hash, byte[] content) throws IOException {
        Path objectPath = getObjectPath(hash);
        Files.createDirectories(objectPath.getParent()); // 객체 파일 경로의 부모 디렉토리 생성
        Files.write(objectPath, content); // 객체 파일 내용 저장
    }

    public String findFullCommitId(String partialCommitId) throws IOException {
        if (partialCommitId == null || partialCommitId.isEmpty()) {
            throw new IllegalArgumentException("Partial commit ID cannot be null or empty");
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(commitsPath)) {
            for (Path commitFile : stream) {
                String fileName = commitFile.getFileName().toString();

                if (fileName.startsWith(partialCommitId)) {
                    return fileName;
                }
            }
        }

        throw new FileSystemException("commit.Commit not found for partial ID: " + partialCommitId);
    }

    public String getHEADValue() throws IOException {
        if (!Files.exists(headPath)) {
            throw new IOException("HEAD file does not exist: " + headPath);
        }

        String headContent = Files.readString(headPath).trim();
        if (headContent.contains("\n") || headContent.contains("\r")) {
            headContent = headContent.split("\\r?\\n")[0].trim();
        }
        return headContent;
    }

    public void updateHEADValue(String value) throws IOException {
        try {
            Files.writeString(headPath, value.trim() + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Failed to update HEAD: " + e.getMessage());
            throw e;
        }
    }

    public void initializeHEAD() throws IOException {
        if (Files.exists(headPath)) {
            System.out.println("HEAD file already exists.");
        } else {
            updateHEADValue("");
            System.out.println("HEAD file initialized.");
        }
    }
}
