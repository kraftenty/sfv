import java.io.IOException;
import java.nio.file.*;
import java.util.Random;

public class DummyFileGenerator {
    private final Path dummyDirectory;
    private final Random random;
    
    public DummyFileGenerator() {
        this.dummyDirectory = Paths.get("dummy");
        this.random = new Random();
    }
    
    public void generateFiles(int numberOfFiles, int fileSizeInMB) throws IOException {
        // 더미 디렉토리 생성
        Files.createDirectories(dummyDirectory);
        
        System.out.println("Generating " + numberOfFiles + " files (" + fileSizeInMB + "MB each)");
        
        byte[] buffer = new byte[1024 * 1024]; // 1MB 버퍼
        for (int i = 0; i < numberOfFiles; i++) {
            Path filePath = dummyDirectory.resolve("dummy-" + i + ".dat");
            
            // 파일 생성 및 데이터 쓰기
            for (int mb = 0; mb < fileSizeInMB; mb++) {
                random.nextBytes(buffer);
                if (mb == 0) {
                    Files.write(filePath, buffer);
                } else {
                    Files.write(filePath, buffer, StandardOpenOption.APPEND);
                }
            }
            
            System.out.printf("Generated file %d/%d%n", i + 1, numberOfFiles);
        }
    }
    
    public void cleanup() throws IOException {
        if (Files.exists(dummyDirectory)) {
            Files.walk(dummyDirectory)
                 .sorted((a, b) -> -a.compareTo(b))
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                     } catch (IOException e) {
                         e.printStackTrace();
                     }
                 });
            System.out.println("Dummy files cleaned up");
        }
    }
} 