import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ChunkedHashCalculator2 {
    private final int chunkSize;
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024; // 1MB

    public ChunkedHashCalculator2() {
        this.chunkSize = DEFAULT_CHUNK_SIZE;
    }

    public String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            int numChunks = (int) Math.ceil((double) fileSize / chunkSize);
            List<String> chunkHashes = new ArrayList<>();

            // 각 청크에 대해 순차적으로 해시 계산
            for (int i = 0; i < numChunks; i++) {
                final long position = (long) i * chunkSize;
                final int size = (int) Math.min(chunkSize, fileSize - position);
                String hash = calculateChunkHash(channel, position, size);
                chunkHashes.add(hash);
            }

            // 모든 청크의 해시값을 결합하여 최종 해시 계산
            return combineHashes(chunkHashes);
        }
    }

    private String calculateChunkHash(FileChannel channel, long position, int size) 
            throws IOException, NoSuchAlgorithmException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        channel.read(buffer, position);
        
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        
        return HashUtil.sha1(bytes);
    }

    private String combineHashes(List<String> chunkHashes) 
            throws NoSuchAlgorithmException {
        StringBuilder combined = new StringBuilder();
        for (String hash : chunkHashes) {
            combined.append(hash);
        }
        return HashUtil.sha1(combined.toString().getBytes());
    }
} 