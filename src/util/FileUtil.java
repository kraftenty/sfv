package util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtil {

    private static final String DOT_SFV = ".sfv";
    private static final String HEAD = "HEAD";
    private static final String COMMIT = "commit";
    private static final String OBJECTS = "objects";

    private static final Path rootPath = Paths.get(".");
    private static final Path dotSfvPath = rootPath.resolve(DOT_SFV);
    private static final Path commitsPath = dotSfvPath.resolve(COMMIT);
    private static final Path objectsPath = dotSfvPath.resolve(OBJECTS);

    public static Path getRootPath() {
        return rootPath;
    }

    public static Path getDotSfvPath() {
        return dotSfvPath;
    }

    public static Path getCommitPath(String commitId) {
        return commitsPath.resolve(commitId);
    }

    public static Path getObjectPath(String hash) {
        return objectsPath.resolve(hash);
    }

    public static void initializeDotSfvDirectory() throws FileSystemException, IOException {
        if (Files.exists(dotSfvPath)) {
            throw new FileSystemException("sfv repository already exists: " + dotSfvPath);
        }
        Files.createDirectory(dotSfvPath);
        Files.createDirectory(objectsPath);
        Files.createDirectory(commitsPath);
        Files.writeString(dotSfvPath.resolve(HEAD), "");
    }

    public static void validateSfvRepository() throws FileSystemException {
        if (!Files.exists(dotSfvPath) || !Files.exists(objectsPath)
                || !Files.exists(commitsPath) || !Files.exists(dotSfvPath.resolve(HEAD))) {
            throw new FileSystemException("sfv repository is not initialized");
        }
    }

    public static String getHEADValue() throws IOException {
        return Files.readString(dotSfvPath.resolve(HEAD)).trim();
    }

    public static void updateHEADValue(String value) throws IOException {
        Files.writeString(dotSfvPath.resolve(HEAD), value);
    }

    public static String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        byte[] fileContent = Files.readAllBytes(file);
        return HashUtil.sha1(fileContent);
    }

    public static void saveObject(String hash, byte[] content) throws IOException {
        Files.write(objectsPath.resolve(hash), content);
    }

    public static long getFileSize(Path file) throws IOException {
        return Files.size(file);
    }

    public static long getLastModifiedTime(Path file) throws IOException {
        return Files.getLastModifiedTime(file).toMillis();
    }

    public static List<String> getAllCommitIds() throws IOException {
        List<String> commitIds = new ArrayList<>();
        if (Files.exists(commitsPath)) {
            try (var stream = Files.list(commitsPath)) {
                stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .forEach(commitIds::add);
            }
        }
        return commitIds;
    }

    public static String findMatchingCommitId(String partialId) throws IOException {
        if (partialId == null || partialId.isEmpty()) {
            throw new IllegalArgumentException("Commit ID cannot be empty");
        }

        List<String> allCommits = getAllCommitIds();
        List<String> matches = allCommits.stream()
                .filter(commitId -> commitId.startsWith(partialId))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            throw new FileSystemException("No matching commit found for: " + partialId);
        }
        if (matches.size() > 1) {
            throw new FileSystemException("Ambiguous commit ID. Matching commits: " + 
                String.join(", ", matches));
        }

        return matches.get(0);
    }


    public static void deleteSfvRepository() throws IOException {
        if (!Files.exists(dotSfvPath)) {
            return;
        }

        // 하위 디렉토리부터 삭제하기 위해 역순으로 순회
        Files.walk(dotSfvPath)
                .sorted((a, b) -> -a.compareTo(b))  // 역순 정렬
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Warning: Could not delete " + path);
                    }
                });
    }


}
