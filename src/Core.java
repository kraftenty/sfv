import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.nio.file.StandardCopyOption;

public class Core {

    private FileUtil fileUtil;

    public Core() {
        this.fileUtil = new FileUtil();
    }

    public void init() throws FileSystemException, IOException {
        fileUtil.initializeDotSfvDirectory();
    }

    public String commit(String message) throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();
        
        // 1. 변경된 파일 찾기
        List<Path> modifiedFiles = findModifiedFiles();
        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }
        
        // 2. 변경된 파일들의 해시 계산 (병렬 처리)
        Map<Path, String> fileHashes = new ConcurrentHashMap<>();
        modifiedFiles.parallelStream().forEach(file -> {
            try {
                String hash = calculateFileHash(file);
                fileHashes.put(file, hash);
                saveObject(hash, Files.readAllBytes(file));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });

        // 3. 커밋 객체 생성 및 저장
        Commit commit = createCommit(message, fileUtil.getHEADValue(), fileHashes);

        // 4. HEAD 업데이트
        fileUtil.updateHEADValue(commit.getId());

        return commit.getId();
    }

    private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(file);
        return HashUtil.sha1(content);
    }

    private void saveObject(String hash, byte[] content) throws IOException {
        Path objectPath = fileUtil.getObjectPath(hash);
        Files.write(objectPath, content);
    }

    // TODO : 병렬 처리 적용 가능
    public void checkout(String commitId) throws IOException {
        fileUtil.validateSfvRepository();
        
        // 1. 커밋 객체 로드
        Commit commit = loadCommit(commitId);
        if (commit == null) {
            throw new FileSystemException("Commit not found: " + commitId);
        }
        
        // 2. 현재 작업 디렉토리의 변경사항 확인
        List<Path> modifiedFiles = findModifiedFiles();
        if (!modifiedFiles.isEmpty()) {
            throw new FileSystemException("You have local changes. Please commit or discard them first.");
        }
        
        // 3. 커밋의 파일 상태로 복원
        for (Map.Entry<String, String> entry : commit.getFileHashes().entrySet()) {
            Path filePath = fileUtil.getRootPath().resolve(entry.getKey());
            String hash = entry.getValue();
            
            // 파일 디렉토리가 없으면 생성
            Files.createDirectories(filePath.getParent());
            
            // objects에서 파일 내용을 복사
            Path objectPath = fileUtil.getObjectPath(hash);
            Files.copy(objectPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // 4. HEAD 업데이트
        fileUtil.updateHEADValue(commitId);
    }

    // TODO : 병렬 처리 적용 가능
    public void getStatus() throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();
        
        // 1. 현재 커밋 정보 가져오기
        String head = fileUtil.getHEADValue();
        Commit currentCommit = head.isEmpty() ? null : loadCommit(head);
        
        // 2. 변경된 파일 찾기
        List<Path> modifiedFiles = findModifiedFiles();
        
        // 3. 상태 출력
        System.out.println("status for commit: " + (currentCommit == null ? "none" :
            currentCommit.getId().substring(0, 7) + " " + currentCommit.getMessage()));

        if (modifiedFiles.isEmpty()) {
            System.out.println("No changes detected");
        } else {
            System.out.println("new changes:");
            for (Path file : modifiedFiles) {
                // 상대 경로로 변환하여 출력
                Path relativePath = fileUtil.getRootPath().relativize(file);
                System.out.println("\tmodified: " + relativePath);
            }
        }
    }

    public void getLog() throws IOException {
        fileUtil.validateSfvRepository();
        
        String currentCommitId = fileUtil.getHEADValue();
        if (currentCommitId.isEmpty()) {
            System.out.println("No commits yet");
            return;
        }
        
        // 커밋 히스토리 순회하며 출력
        Commit currentCommit = loadCommit(currentCommitId);
        while (currentCommit != null) {
            System.out.println("commit " + currentCommit.getId());
            System.out.println("Date: " + currentCommit.getTimestamp());
            System.out.println("message: " + currentCommit.getMessage());
            System.out.println("-----------------------------------------------");
            
            // 이전 커밋으로 이동
            String previousCommitId = currentCommit.getPreviousCommitId();
            if (previousCommitId.isEmpty()) {
                break;
            }
            currentCommit = loadCommit(previousCommitId);
        }
    }

    // TODO : 병렬 처리 적용 가능
    private List<Path> findModifiedFiles() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        Files.walk(fileUtil.getRootPath())
            .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
            .filter(path -> !path.toString().contains("/."))  // .으로 시작하는 모든 디렉토리 제외
            .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))  // out 디렉토리 제외
            .filter(Files::isRegularFile)
            .filter(Files::exists)
            .forEach(path -> {
                try {
                    String currentHash = calculateFileHash(path);
                    String storedHash = getStoredHash(path);
//                    System.out.println("currentHash = " + currentHash + ", storedHash = " + storedHash);
                    if (!currentHash.equals(storedHash)) {
                        modifiedFiles.add(path);
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    System.err.println("Warning: Could not access file: " + path);
                }
            });
        return modifiedFiles;
    }

    private String getStoredHash(Path file) throws IOException {
        String head = fileUtil.getHEADValue();
        if (head.isEmpty()) {
            return "__firsthash__";
        }
        
        Commit lastCommit = loadCommit(head);
        if (lastCommit == null) {
            return "__firsthash__";
        }
        
        Path relativePath = fileUtil.getRootPath().relativize(file);
        String normalizedPath = relativePath.normalize().toString();

        return lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
    }

    private Commit createCommit(String message, String HEAD, Map<Path, String> fileHashes) throws NoSuchAlgorithmException, IOException {
        String commitId = generateCommitId(message);
        Path commitPath = fileUtil.getCommitPath(commitId);
        
        if (Files.exists(commitPath)) {
            return loadCommit(commitId);
        }
        
        // 이전 커밋의 파일 해시 정보를 가져옴
        Map<String, String> relativeFileHashes = new ConcurrentHashMap<>();
        if (!HEAD.isEmpty()) {
            Commit previousCommit = loadCommit(HEAD);
            if (previousCommit != null) {
                relativeFileHashes.putAll(previousCommit.getFileHashes());
            }
        }
        
        // 새로운 변경사항 추가
        for (Map.Entry<Path, String> entry : fileHashes.entrySet()) {
            Path relativePath = fileUtil.getRootPath().relativize(entry.getKey());
            String normalizedPath = relativePath.normalize().toString();
            relativeFileHashes.put(normalizedPath, entry.getValue());
        }
        
        Commit commit = new Commit(commitId, message, HEAD, relativeFileHashes);
        saveCommit(commit);
        return commit;
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

    private Commit loadCommit(String commitId) throws IOException {
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

}
