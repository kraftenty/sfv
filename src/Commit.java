import java.io.Serializable;
import java.time.LocalDateTime;

public class Commit implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String message;
    private final LocalDateTime timestamp;
    private final String previousCommitId;

    public Commit(String id, String message, String previousCommitId) {
        this.id = id;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.previousCommitId = previousCommitId;
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



    @Override
    public String toString() {
        return String.format("Commit: %s\nDate: %s\nMessage: %s", id, timestamp, message);
    }
}
