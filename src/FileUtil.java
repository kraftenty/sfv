import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class FileUtil {

    private static final String DOT_SFV = ".sfv";
    private static final String HEAD = "HEAD";
    private static final String COMMIT = "commit";
    private static final String OBJECT = "object";
    private final Path rootPath;
    private final Path dotSfvPath;
    private final Path commitsPath;
    private final Path objectsPath;

    public FileUtil() {
        this.rootPath = Paths.get(".");
        this.dotSfvPath = rootPath.resolve(DOT_SFV);
        this.commitsPath = dotSfvPath.resolve(COMMIT);
        this.objectsPath = dotSfvPath.resolve(OBJECT);
    }

    public void initializeDotSfvDirectory() throws FileSystemException, IOException {
        if (Files.exists(dotSfvPath)) {
            throw new FileSystemException("sfv repository already exists: " + dotSfvPath);
        }
        // .sfv 디렉토리 생성
        Files.createDirectory(dotSfvPath);
        // objects 디렉토리 생성
        Files.createDirectory(objectsPath);
        // commits 디렉토리 생성
        Files.createDirectory(commitsPath);
        // heads 파일 생성 및 초기화
        Files.writeString(dotSfvPath.resolve(HEAD), "");
    }

    public void validateSfvRepository() throws FileSystemException {
        if (!Files.exists(dotSfvPath) || !Files.exists(objectsPath)
                || !Files.exists(commitsPath) || !Files.exists(dotSfvPath.resolve(HEAD))) {
            throw new FileSystemException("sfv repository is not initialized");
        }
    }

    public String getHEADValue() throws IOException {
        return Files.readString(dotSfvPath.resolve(HEAD)).trim();
    }

    public void updateHEADValue(String value) throws IOException {
        Files.writeString(dotSfvPath.resolve(HEAD), value);
    }

    public void saveObject(String hash, byte[] content) throws IOException {
        Path objectPath = getObjectPath(hash);
        Files.write(objectPath, content);
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
        return commitsPath.resolve(commitId);
    }

    public Path getObjectPath(String hash) {
        return objectsPath.resolve(hash);
    }
}
