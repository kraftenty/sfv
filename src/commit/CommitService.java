package commit;

import util.FileUtil;
import util.HashUtil;

import java.io.*;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CommitService {

    public void commit(String message) throws IOException, NoSuchAlgorithmException {
        FileUtil.validateSfvRepository();

        // 1. 현재 작업 디렉토리의 모든 파일 목록 가져오기
        Set<Path> currentFiles = Files.walk(FileUtil.getRootPath())
                .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.toString().contains("/out"))
                .filter(Files::isRegularFile)
                .collect(Collectors.toSet());

        // 2. 변경된 파일 찾기
        List<Path> modifiedFiles = ModifyDetector.findModifiedFiles();
        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }

        Map<Path, String> fileHashes = getFileHashMapV1(modifiedFiles);

        // 3. 커밋 객체 생성 및 저장 (현재 존재하는 파일만 포함)
        Map<String, String> newFileMetadata = new HashMap<>();
        for (Path file : currentFiles) {
            String normalizedPath = FileUtil.getRootPath().relativize(file).normalize().toString();
            long fileSize = FileUtil.getFileSize(file);
            long lastModifiedTime = FileUtil.getLastModifiedTime(file);
            String hash = FileUtil.calculateFileHash(file);
            String fileInfo = fileSize + "," + lastModifiedTime + "," + hash;
            newFileMetadata.put(normalizedPath, fileInfo);
        }

        Commit commit = new Commit(generateCommitId(message), message, FileUtil.getHEADValue(), newFileMetadata);
        saveCommitToCommitDirectory(commit);

        // 4. HEAD 업데이트
        FileUtil.updateHEADValue(commit.getId());
        System.out.println("[commit] commited " + commit.getId().substring(0, 7) + " (" + message + ")");
    }

    // V1 : 싱글 스레드
    private static Map<Path, String> getFileHashMapV1(List<Path> modifiedFiles) {
        Map<Path, String> fileHashes = new HashMap<>();
        for (Path file : modifiedFiles) {
            try {
                String hash = FileUtil.calculateFileHash(file);
                fileHashes.put(file, hash);
                FileUtil.saveObject(hash, Files.readAllBytes(file));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return fileHashes;
    }

    // V2 : ParallelStream 사용
    private static Map<Path, String> getFileHashMapV2(List<Path> modifiedFiles) {
        Map<Path, String> fileHashes = new ConcurrentHashMap<>();
        modifiedFiles.parallelStream().forEach(file -> {
            try {
                String hash = FileUtil.calculateFileHash(file);
                fileHashes.put(file, hash);
                FileUtil.saveObject(hash, Files.readAllBytes(file));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });
        return fileHashes;
    }

    // V3 : ThreadpoolExecutor
    private static Map<Path, String> getFileHashMapV3(List<Path> modifiedFiles) {
        Map<Path, String> fileHashes = new ConcurrentHashMap<>();
        // 알고리즘 구현
        return fileHashes;
    }

    // V4 : 생산자-소비자 패턴 구현
    private static Map<Path, String> getFileHashMapV4(List<Path> modifiedFiles) {
        Map<Path, String> fileHashes = new ConcurrentHashMap<>();
        // 알고리즘 구현
        return fileHashes;
    }

    public static Commit createCommit(String message, String HEAD, Map<Path, String> fileHashes) throws NoSuchAlgorithmException, IOException {
        // 이전 커밋의 파일 해시 정보를 가져옴
        Map<String, String> fileHashMap = new HashMap<>(); // key : 파일 상대경로, value : 해시값
        if (!HEAD.isEmpty()) {
            Commit previousCommit = loadCommitFromCommitDirectory(HEAD);
            if (previousCommit != null) {
                fileHashMap.putAll(previousCommit.getFileMetadataMap());
            }
        }

        // 새로운 변경사항 추가
        for (Map.Entry<Path, String> entry : fileHashes.entrySet()) {
            String normalizedPath = FileUtil.getRootPath().relativize(entry.getKey()).normalize().toString();
            long fileSize = FileUtil.getFileSize(entry.getKey());
            long lastModifiedTime = FileUtil.getLastModifiedTime(entry.getKey());
            String hash = entry.getValue();
            String fileInfo = fileSize + "," + lastModifiedTime + "," + hash;
            fileHashMap.put(normalizedPath, fileInfo);
        }

        return new Commit(generateCommitId(message), message, HEAD, fileHashMap);
    }

    public static Commit loadCommitFromCommitDirectory(String commitId) throws IOException {
        try {
            Path commitPath = FileUtil.getCommitPath(commitId);
            if (!Files.exists(commitPath)) {
                throw new IOException("Commit file not exists: " + commitPath);
            }
            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(commitPath));
            Commit commit = (Commit) ois.readObject();
            ois.close();
            return commit;
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load commit: " + commitId, e);
        }
    }

    private static void saveCommitToCommitDirectory(Commit commit) {
        try {
            Path commitPath = FileUtil.getCommitPath(commit.getId());
            ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(commitPath));
            oos.writeObject(commit);
            oos.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // commit id 생성 메서드
    private static String generateCommitId(String message) throws NoSuchAlgorithmException {
        String content = message + LocalDateTime.now().toString();
        return HashUtil.sha1(content.getBytes());
    }

}
