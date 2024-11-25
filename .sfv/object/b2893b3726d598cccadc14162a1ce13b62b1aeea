import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;

public class HashUtil {

    // SHA-1 해시 계산
    public static String sha1(byte[] content) throws NoSuchAlgorithmException {
        // SHA-1 알고리즘을 사용하는 MessageDigest 객체 생성
        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        // 콘텐츠의 바이트 배열을 해싱
        byte[] hashBytes = digest.digest(content);

        // 바이트 배열을 16진수 문자열로 변환하여 반환
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));  // 각 바이트를 두 자릿수의 16진수로 변환
        }
        return hexString.toString();  // 16진수 해시값을 문자열로 반환
    }

    // 바이트 배열을 SHA-1로 계산하여 반환
    public static String sha1FromFile(java.nio.file.Path file) throws IOException, NoSuchAlgorithmException {
        byte[] content = java.nio.file.Files.readAllBytes(file);  // 파일 내용 읽기
        return sha1(content);  // 읽은 내용을 해시 계산
    }
}
