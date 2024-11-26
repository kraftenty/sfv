package timer;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class PerformanceTimer {
    private static final Map<String, List<TimerRecord>> timerRecords = new HashMap<>();
    private static final Map<String, Long> startTimes = new HashMap<>();

    public static void start(String operationName) {
        startTimes.put(operationName, System.nanoTime());
    }

    public static void stop(String operationName) {
        Long startTime = startTimes.remove(operationName);
        if (startTime == null) {
            throw new IllegalStateException("Timer was not started for: " + operationName);
        }

        long duration = System.nanoTime() - startTime;
        timerRecords.computeIfAbsent(operationName, k -> new ArrayList<>())
                   .add(new TimerRecord(duration, System.currentTimeMillis()));
    }

    public static void printStats(String operationName) {
        List<TimerRecord> records = timerRecords.get(operationName);
        if (records == null || records.isEmpty()) {
            System.out.println("No timing data for: " + operationName);
            return;
        }

        double totalMs = records.stream()
                              .mapToLong(TimerRecord::getDuration)
                              .sum() / 1_000_000.0;
        
        double avgMs = totalMs / records.size();
        double minMs = records.stream()
                            .mapToLong(TimerRecord::getDuration)
                            .min()
                            .orElse(0) / 1_000_000.0;
        double maxMs = records.stream()
                            .mapToLong(TimerRecord::getDuration)
                            .max()
                            .orElse(0) / 1_000_000.0;

        System.out.println("\nPerformance Statistics for: " + operationName);
        System.out.println("Total executions: " + records.size());
        System.out.println(String.format("Average time: %.2f ms", avgMs));
        System.out.println(String.format("Min time: %.2f ms", minMs));
        System.out.println(String.format("Max time: %.2f ms", maxMs));
        System.out.println(String.format("Total time: %.2f ms", totalMs));
    }

    public static void reset(String operationName) {
        timerRecords.remove(operationName);
        startTimes.remove(operationName);
    }

    public static void resetAll() {
        timerRecords.clear();
        startTimes.clear();
    }

    private static class TimerRecord {
        private final long duration;
        private final long timestamp;

        TimerRecord(long duration, long timestamp) {
            this.duration = duration;
            this.timestamp = timestamp;
        }

        public long getDuration() {
            return duration;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
} 