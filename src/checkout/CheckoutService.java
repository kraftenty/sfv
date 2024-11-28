package checkout;

import commit.CommitService;
import commit.ModifyDetector;
import util.FileUtil;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class CheckoutService {

    public void checkout(String partialTargetCommitId) throws IOException {
        FileUtil.validateSfvRepository();

        // 1. 완전한 커밋 ID 찾기 (커밋ID 를 다 입력하는건 불편하므로, partialCommitId를 입력받은다음 fullCommitId로 변환)
        String targetCommitId = FileUtil.findMatchingCommitId(partialTargetCommitId);
        System.out.println("[checkout] " + FileUtil.getHEADValue() + " -> " + targetCommitId);

        // 2. 현재 작업 디렉토리의 커밋 후 변경사항 확인. 커밋 후 변경사항이 있으면 안됨.
        // 반드시 커밋 후에 체크아웃 요망
        List<Path> modifiedFiles = ModifyDetector.findModifiedFiles();
        if (!modifiedFiles.isEmpty()) {
            throw new FileSystemException("You have changes after commit. Please commit them first.");
        }

        // 3. 현재 커밋, 타겟 커밋의 정보 가져오기
        // key: src/Main.java
        // value: 923,1732756949846,2b1903dbff5764c8a2829759740f8fec68d05c56
        // ...
        Map<String, String> currentCommitMetadataMap = CommitService.loadCommitFromCommitDirectory(FileUtil.getHEADValue()).getFileMetadataMap();
        Map<String, String> targetCommitMetadataMap = CommitService.loadCommitFromCommitDirectory(targetCommitId).getFileMetadataMap();


        // 4-1. 파일 갱신 (삭제)
        Set<String> filesToDelete = new HashSet<>(currentCommitMetadataMap.keySet());
        filesToDelete.removeAll(targetCommitMetadataMap.keySet());
        for (String filePath : filesToDelete) {
            System.out.println("\tdeleting " + filePath);
            Files.delete(FileUtil.getRootPath().resolve(filePath));
        }

        // 4-2. 파일 갱신 (복원, 수정) TODO : 병렬처리 가능 구간
        restoreFileV1(targetCommitMetadataMap, currentCommitMetadataMap); // TODO : 여기서 알고리즘 갈아끼우셈

        // 5. 빈 디렉토리 정리
        cleanEmptyDirectories(FileUtil.getRootPath());

        // 6. HEAD 업데이트
        FileUtil.updateHEADValue(targetCommitId);

        System.out.println("checkout complete.");
    }

    // V1 : 싱글 스레드
    private static void restoreFileV1(Map<String, String> targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException {
        for (Map.Entry<String, String> entry : targetCommitMetadataMap.entrySet()) {
            String targetFilePath = entry.getKey();
            String targetFileHash = entry.getValue().split(",")[2];
            
            // 현재 커밋에 파일이 없거나 해시값이 다른 경우 복원
            if (!currentCommitMetadataMap.containsKey(targetFilePath) || 
                !currentCommitMetadataMap.get(targetFilePath).split(",")[2].equals(targetFileHash)) {
                Path filePath = FileUtil.getRootPath().resolve(targetFilePath);
                Files.createDirectories(filePath.getParent());
                System.out.println("\trestoring " + filePath);
                Files.copy(FileUtil.getObjectPath(targetFileHash), filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // V2 :
    private static void restoreFileV2(String targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException {
        // 알고리즘 구현
    }

    // V3 :
    private static void restoreFileV3(String targetCommitMetadataMap, Map<String, String> currentCommitMetadataMap) throws IOException {
        // 알고리즘 구현
    }

    // 빈 디렉토리를 정리하는 헬퍼 메서드
    private void cleanEmptyDirectories(Path directory) throws IOException {
        if (directory.equals(FileUtil.getDotSfvPath())) {
            return;  // .sfv 디렉토리는 건드리지 않음
        }

        Files.walk(directory)
                .sorted(Comparator.reverseOrder())  // 하위 디렉토리부터 처리하기 위해 역순 정렬
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(directory))  // 루트 디렉토리는 제외
                .filter(path -> !path.equals(FileUtil.getDotSfvPath()))  // .sfv 디렉토리는 제외
                .forEach(path -> {
                    try {
                        if (Files.list(path).count() == 0) {  // 디렉토리가 비어있는지 확인
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: Could not process directory: " + path);
                    }
                });
    }
}
