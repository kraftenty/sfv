import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public class ParallelStrategyImplV1 implements ParallelStrategy {
    @Override
    public void processFilesInParallel(List<Path> files, FileProcessor processor) {
        files.parallelStream().forEach(file -> {
            try {
                processor.process(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
