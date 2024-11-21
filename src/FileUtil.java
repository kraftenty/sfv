import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.io.IOException;

public class FileUtil {

    private static final String DOT_SFV = ".sfv";
    private static final String HEAD = "HEAD";
    private final Path rootPath;
    private final Path dotSfvPath;

    public FileUtil() {
        this.rootPath = Paths.get(".");
        this.dotSfvPath = rootPath.resolve(DOT_SFV);
    }

    public void initializeDotSfvDirectory() throws FileSystemException, IOException {
        if (Files.exists(dotSfvPath)) {
            throw new FileSystemException("sfv repository already exists: " + dotSfvPath);
        }
        // .sfv 디렉토리 생성
        Files.createDirectory(dotSfvPath);
        // objects 디렉토리 생성
        Files.createDirectory(dotSfvPath.resolve("objects"));
        // commits 디렉토리 생성
        Files.createDirectory(dotSfvPath.resolve("commits"));
        // heads 파일 생성 및 초기화
        Files.writeString(dotSfvPath.resolve("HEAD"), "");
    }

    public void validateSfvRepository() throws FileSystemException {
        if (!Files.exists(dotSfvPath) || !Files.exists(dotSfvPath.resolve("objects"))
                || !Files.exists(dotSfvPath.resolve("commits")) || !Files.exists(dotSfvPath.resolve("HEAD"))) {
            throw new FileSystemException("sfv repository is not initialized");
        }
    }

    public String getHEADValue() throws IOException {
        return Files.readString(dotSfvPath.resolve(HEAD)).trim();
    }

    public void updateHEADValue(String value) throws IOException {
        Files.writeString(dotSfvPath.resolve(HEAD), value);
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Path getDotSfvPath() {
        return dotSfvPath;
    }

    public Path getCommitPath(String commitId) throws IOException {
        Path commitPath = dotSfvPath.resolve("commits").resolve(commitId);
        Files.createDirectories(commitPath.getParent());
        return commitPath;
    }

    public Path getObjectPath(String hash) throws IOException {
        Path objectPath = dotSfvPath.resolve("objects").resolve(hash);
        Files.createDirectories(objectPath.getParent());
        return objectPath;
    }
}
