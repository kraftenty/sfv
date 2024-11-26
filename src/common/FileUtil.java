package common;

import java.nio.file.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class FileUtil {
    private static final String DOT_SFV = ".sfv";
    private static final String COMMIT = "commit";
    private static final String OBJECT = "object";
    private static final String HEAD = "HEAD";

    private static final Path ROOT_PATH = Paths.get(".");
    private static final Path DOT_SFV_PATH = ROOT_PATH.resolve(DOT_SFV);
    private static final Path COMMITS_PATH = Paths.get(DOT_SFV_PATH.toString(), COMMIT);
    private static final Path OBJECTS_PATH = Paths.get(DOT_SFV_PATH.toString(), OBJECT);
    private static final Path HEAD_PATH = Paths.get(DOT_SFV_PATH.toString(), HEAD);

    public static void initializeDotSfv() throws IOException {
        // .sfv 디렉토리가 이미 존재하는지 확인
        if (Files.exists(DOT_SFV_PATH)) {
            throw new IOException("sfv repository already exists at: " + DOT_SFV_PATH);
        }

        // .sfv 디렉토리와 하위 디렉토리 생성
        Files.createDirectory(DOT_SFV_PATH);
        Files.createDirectory(OBJECTS_PATH);
        Files.createDirectory(COMMITS_PATH);
    }

    public static void validateSfvRepository() throws FileSystemException {
        if (!Files.exists(DOT_SFV_PATH) || !Files.exists(OBJECTS_PATH)
                || !Files.exists(COMMITS_PATH)) {
            throw new FileSystemException("sfv repository is not initialized");
        }
    }

    public static String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(file);
        return HashUtil.sha1(content);
    }

    public static Path getRootPath() {
        return ROOT_PATH;
    }

    public static Path getDotSfvPath() {
        return DOT_SFV_PATH;
    }

    public static Path getCommitPath(String commitId) {
        if (commitId == null || commitId.isEmpty()) {
            throw new IllegalArgumentException("Invalid commit ID: " + commitId);
        }
        return COMMITS_PATH.resolve(commitId);
    }

    public static Path getObjectPath(String hash) {
        if (hash == null || hash.isEmpty()) {
            throw new IllegalArgumentException("Invalid hash: " + hash);
        }
        return OBJECTS_PATH.resolve(hash);
    }

    public static void saveObject(String hash, byte[] content) throws IOException {
        Path objectPath = getObjectPath(hash);
        Files.createDirectories(objectPath.getParent());
        Files.write(objectPath, content);
    }

    public static String getHEADValue() throws IOException {
        if (!Files.exists(HEAD_PATH)) {
            throw new IOException("HEAD file does not exist");
        }
        return Files.readString(HEAD_PATH).trim();
    }

    public static void updateHEADValue(String value) throws IOException {
        Files.writeString(HEAD_PATH, value + System.lineSeparator());
    }

    public static void initializeHEAD() throws IOException {
        updateHEADValue("");
    }

    public static String findFullCommitId(String partialCommitId) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(COMMITS_PATH)) {
            for (Path commitFile : stream) {
                String fileName = commitFile.getFileName().toString();
                if (fileName.startsWith(partialCommitId)) {
                    return fileName;
                }
            }
        }
        throw new FileSystemException("Commit not found for partial ID: " + partialCommitId);
    }

    // 윈도우에서도 경로구분자 통일. 이거갖다쓰셈
    public static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}