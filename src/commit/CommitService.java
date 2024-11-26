package commit;

import timer.PerformanceTimer;
import common.FileUtil;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

public class CommitService {

    private final FileUtil fileUtil;
    private final CommitUtil commitUtil;

    public CommitService() {
        fileUtil = new FileUtil();
        commitUtil = new CommitUtil();
    }

    // commit.Commit
    public String commit(String message) throws IOException, NoSuchAlgorithmException {
        fileUtil.validateSfvRepository();

        // 1. 변경된 파일 찾기
        PerformanceTimer.start("find-modified-files");
        List<Path> modifiedFiles = findModifiedFiles3();
        PerformanceTimer.stop("find-modified-files");
        System.out.println("\nfind-modified-files Performance:");
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
        System.out.println("\nHash Calculation Performance:");
        PerformanceTimer.printStats("hash-calculation");


        // 3. 커밋 객체 생성 및 저장
        Commit commit = commitUtil.createCommit(message, fileUtil.getHEADValue(), fileHashes); // TODO

        // 4. HEAD 업데이트
        fileUtil.updateHEADValue(commit.getId()); // TODO

        return commit.getId();
    }



}
