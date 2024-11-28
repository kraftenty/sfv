package commit;

import util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModifyDetector {

    // TODO : 병렬처리 가능

    /**
     * 1. 청크 기반 분할(전체 파일 리스트를 N개의 동일한 청크로 나눠서, 각 스레드가 하나의 청크를 담당)
     * 2. Work Queue 방식 병렬처리(모든 파일을 작업 큐에 넣고, 여러 워커 스레드가 큐에서 작업을 가져가서 처리)
     */

    public static List<Path> findModifiedFiles() throws IOException {
        return doStrategyV1(); // TODO : 여기서 알고리즘 갈아끼우기
    }

    // V1 전략 : 싱글스레드
    private static List<Path> doStrategyV1() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        String head = FileUtil.getHEADValue();
        Commit lastCommit = head.isEmpty() ? null : CommitService.loadCommitFromCommitDirectory(head);

        Files.walk(FileUtil.getRootPath())
                .filter(path -> !path.startsWith(FileUtil.getDotSfvPath()))
                .filter(path -> !path.toString().contains("/."))
                .filter(path -> !path.toString().contains("/out"))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        long currentSize = FileUtil.getFileSize(path);
                        long currentModifiedTime = FileUtil.getLastModifiedTime(path);

                        if (lastCommit != null) {
                            Path relativePath = FileUtil.getRootPath().relativize(path);
                            String normalizedPath = relativePath.normalize().toString();
                            String storedInfo = lastCommit.getFileMetadataMap().get(normalizedPath);

                            if (storedInfo != null) {
                                String[] parts = storedInfo.split(",");
                                long storedSize = Long.parseLong(parts[0]);
                                long storedModifiedTime = Long.parseLong(parts[1]);

                                if (currentSize != storedSize || currentModifiedTime != storedModifiedTime) {
                                    modifiedFiles.add(path);
                                }
                            } else {
                                modifiedFiles.add(path);
                            }
                        } else {
                            modifiedFiles.add(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: Could not access file: " + path);
                    }
                });

        return modifiedFiles;
    }

    // V2 전략 : ...
    private static List<Path> doStrategyV2() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        // 알고리즘 구현
        return modifiedFiles;
    }

    // V3 전략 : ...
    private static List<Path> doStrategyV3() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        // 알고리즘 구현
        return modifiedFiles;
    }

    // V4 전략 : ...
    private static List<Path> doStrategyV4() throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        // 알고리즘 구현
        return modifiedFiles;
    }


}
