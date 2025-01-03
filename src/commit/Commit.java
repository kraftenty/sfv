package commit;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Commit implements Serializable {
    /**
     * 커밋 객체는 직렬화되서 파일로 commit 폴더에 저장된다.
     * 파일명은 커밋 객체의 id로 저장된다.
     * 
     * commit.Commit {
     *     id: "5e67ec1be88ffb2be8d69b5c5490af8d42461e7b"
     *     message: "사용자가 입력한 커밋 메시지"
     *     timestamp: "커밋 생성 시간"
     *     previousCommitId: "이전 커밋의 해시값"
     *     fileHashes: {
     *         "파일경로1": "해당 파일의 해시값1",
     *         "파일경로2": "해당 파일의 해시값2",
     *         ... 
     *     }
     *  }
     */
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String message;
    private final LocalDateTime timestamp;
    private final String previousCommitId;
    private final Map<String, String> fileHashes;

    public Commit(String id, String message, String previousCommitId, Map<String, String> fileHashes) {
    this.id = id;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.previousCommitId = previousCommitId;
        this.fileHashes = new ConcurrentHashMap<>(fileHashes);
    }

    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getPreviousCommitId() {
        return previousCommitId;
    }

    /**
     * 커밋의 파일 메타데이터를 가져오는 메서드
     * @return key:경로, value:파일크기,수정시간,해시값
     */
    public Map<String, String> getFileMetadataMap() {
        return Collections.unmodifiableMap(fileHashes);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("commit.Commit: %s\nDate: %s\nMessage: %s\nPrevious commit.Commit: %s",
            id.substring(0, 7), 
            timestamp, 
            message,
            previousCommitId.isEmpty() ? "none" : previousCommitId.substring(0, 7));
    }
}
