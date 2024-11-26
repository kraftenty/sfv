package commit;

import common.FileChangeDetector;
import common.HashUtil;
import common.PerformanceTimer;
import common.FileUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommitService {

    public String commit(String message) throws IOException, NoSuchAlgorithmException {
        FileUtil.validateSfvRepository();

        // 1. 변경된 파일 찾기
        PerformanceTimer.start("find-modified-files");
        List<Path> modifiedFiles = FileChangeDetector.findChangedFiles(FileChangeDetector.Strategy.PARALLEL_CHUNKED);
        PerformanceTimer.stop("find-modified-files");
        PerformanceTimer.printStats("find-modified-files");
        if (modifiedFiles.isEmpty()) {
            throw new FileSystemException("Nothing to commit.");
        }

//         PerformanceTimer.start("hash-calculation");
//         // 2. 변경된 파일들의 해시 계산 (병렬 처리) - ParallelStream : 자동 병렬처리. 수동 병렬처리 연구 해보셈 TODO
//         Map<Path, String> fileHashes = new ConcurrentHashMap<>();
//         modifiedFiles.parallelStream().forEach(file -> {
//             try {
//                 String hash = fileUtil.calculateFileHash(file);
//                 fileHashes.put(file, hash);
//                 fileUtil.saveObject(hash, Files.readAllBytes(file));
//             } catch (IOException | NoSuchAlgorithmException e) {
//                 throw new RuntimeException(e);
//             }
//         });
//         PerformanceTimer.stop("hash-calculation");
//         System.out.println("\nHash Calculation Performance:");
//         PerformanceTimer.printStats("hash-calculation");
        //2. 병렬 처리를 위한 executor 생성 및 해시 계산  //
        PerformanceTimer.start("hash-calculation");
        FileHashExecutor executor = new FileHashExecutor(Runtime.getRuntime().availableProcessors());
        Map<Path, String> fileHashes = executor.calculateHashes(modifiedFiles);
        executor.shutdown();
        PerformanceTimer.stop("hash-calculation");

        // 성능 통계 출력
        PerformanceTimer.printStats("hash-calculation");


        // 3. 커밋 객체 생성 및 저장
        Commit commit = createCommit(message, FileUtil.getHEADValue(), fileHashes); // TODO

        // 4. HEAD 업데이트
        FileUtil.updateHEADValue(commit.getId()); // TODO

        return commit.getId();
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
            Path relativePath = FileUtil.getRootPath().relativize(entry.getKey());
            String normalizedPath = relativePath.normalize().toString();
            fileHashMap.put(normalizedPath, entry.getValue());
        }

        Commit commit = new Commit(generateCommitId(message), message, HEAD, fileHashMap);
        saveCommit(commit);
        return commit;
    }

    public static Commit loadCommit(String commitId) throws IOException {
        try {
            Path commitPath = FileUtil.getCommitPath(commitId);
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


    private void saveCommit(Commit commit) throws IOException {
        Path commitPath = FileUtil.getCommitPath(commit.getId());
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
