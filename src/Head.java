import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Head {
    private final Path dotSfvPath; // .sfv 디렉토리 경로
    private final Path headPath; // HEAD 파일 경로

    public Head(Path dotSfvPath) {
        this.dotSfvPath = dotSfvPath;
        this.headPath = Paths.get(dotSfvPath.toString(), "HEAD");
    }

    // HEAD 값 읽기
    public String getValue() throws IOException {
        if (!Files.exists(headPath)) {
            throw new IOException("HEAD file does not exist: " + headPath);
        }

        String headContent = Files.readString(headPath).trim(); // 공백 제거
        if (headContent.contains("\n") || headContent.contains("\r")) {
            // 여러 줄이 포함된 경우 첫 번째 줄만 사용
            headContent = headContent.split("\\r?\\n")[0].trim();
        }
        return headContent;
    }


    // HEAD 값 설정
    public void setValue(String value) throws IOException {
        try {

            // 파일 쓰기
            Files.writeString(headPath, value.trim() + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Failed to update HEAD: " + e.getMessage());
            throw e; // 예외 재발생
        }
    }

    // HEAD 파일 초기화
    public void initialize() throws IOException {
        if (Files.exists(headPath)) {
            System.out.println("HEAD file already exists.");
        } else {
            setValue(""); // 빈 값으로 초기화
            System.out.println("HEAD file initialized.");
        }
    }

}
