import commit.Commit;
import commit.CommitUtil;
import common.FileUtil;

import java.io.IOException;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Core {

    private final FileUtil fileUtil;
    private final CommitUtil commitUtil;

    public Core() {
        this.fileUtil = new FileUtil();
        this.commitUtil = new CommitUtil();
    }
















    // TODO : 병렬 처리 적용 가능
    private List<Path> findModifiedFiles() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        List<Path> allFiles = new ArrayList<>();
        
        // 1. 모든 대상 파일 수집
        Files.walk(fileUtil.getRootPath())
            .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
            .filter(path -> !path.toString().contains("/."))
            .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))
            .filter(Files::isRegularFile)
            .filter(Files::exists)
            .forEach(allFiles::add);

        // 2. ChunkedHashCalculator 초기화
        ChunkedHashCalculator hashCalculator = new ChunkedHashCalculator(
            Runtime.getRuntime().availableProcessors() //cpu 코어 수 만큼 스레드 할당
        );

        try {
            // 3. HEAD 값과 마지막 커밋 가져오기
            String head = fileUtil.getHEADValue();
            Commit lastCommit = head.isEmpty() ? null : commitUtil.loadCommit(head);

            // 4. 각 파일에 대해 청크 기반 해시 계산 및 비교
            for (Path path : allFiles) {
                try {
                    String currentHash = hashCalculator.calculateFileHash(path);
                    String storedHash = "__firsthash__";

                    if (lastCommit != null) {
                        Path relativePath = fileUtil.getRootPath().relativize(path);
                        String normalizedPath = relativePath.normalize().toString();
                        storedHash = lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
                    }

                    if (!currentHash.equals(storedHash)) {
                        modifiedFiles.add(path);
                    }
                } catch (NoSuchAlgorithmException e) {
                    System.err.println("Warning: Hash calculation failed for file: " + path);
                }
            }
        } finally {
            // 5. 리소스 정리
            hashCalculator.shutdown();
        }

        return modifiedFiles;
    }

    // TODO : 병렬 처리 적용 가능
    private List<Path> findModifiedFiles2() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        Files.walk(fileUtil.getRootPath())
                .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))  // .으로 시작하는 모든 디렉토리 제외
                .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))  // out 디렉토리 제외
                .filter(Files::isRegularFile)
                .filter(Files::exists)
                .forEach(path -> {
                    try {
                        String currentHash = fileUtil.calculateFileHash(path);

                        // 저장된 해시값 가져오기
                        String head = fileUtil.getHEADValue();
                        String storedHash = "__firsthash__";

                        if (!head.isEmpty()) {
                            Commit lastCommit = commitUtil.loadCommit(head);
                            if (lastCommit != null) {
                                Path relativePath = fileUtil.getRootPath().relativize(path);
                                String normalizedPath = relativePath.normalize().toString();
                                storedHash = lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
                            }
                        }

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

    private List<Path> findModifiedFiles3() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        List<Path> allFiles = new ArrayList<>();

        // 1. 모든 대상 파일 수집
        Files.walk(fileUtil.getRootPath())
                .filter(path -> !path.startsWith(fileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.startsWith(fileUtil.getRootPath().resolve("out")))
                .filter(Files::isRegularFile)
                .filter(Files::exists)
                .forEach(allFiles::add);

        // 2. ChunkedHashCalculator2 초기화 (싱글스레드)
        ChunkedHashCalculator2 hashCalculator = new ChunkedHashCalculator2();

        // 3. HEAD 값과 마지막 커밋 가져오기
        String head = fileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : commitUtil.loadCommit(head);

        // 4. 각 파일에 대해 청크 기반 해시 계산 및 비교
        for (Path path : allFiles) {
            try {
                String currentHash = hashCalculator.calculateFileHash(path);
                String storedHash = "__firsthash__";

                if (lastCommit != null) {
                    Path relativePath = fileUtil.getRootPath().relativize(path);
                    String normalizedPath = relativePath.normalize().toString();
                    storedHash = lastCommit.getFileHashes().getOrDefault(normalizedPath, "");
                }

                if (!currentHash.equals(storedHash)) {
                    modifiedFiles.add(path);
                }
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Warning: Hash calculation failed for file: " + path);
            }
        }

        return modifiedFiles;
    }


}