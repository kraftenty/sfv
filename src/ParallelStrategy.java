import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ParallelStrategy {
    void processFilesInParallel(List<Path> files, FileProcessor processor);
    
    interface FileProcessor {
        void process(Path file) throws IOException;
    }
}
