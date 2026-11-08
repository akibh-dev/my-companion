import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FilesAndThreads {
    public static void main(String[] args)
            throws IOException, InterruptedException, ExecutionException {
        Path path = Files.createTempFile("companion-learning-", ".txt");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Files.writeString(path, "Drink water\n", StandardCharsets.UTF_8);
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (!"Drink water".equals(reader.readLine())) {
                    throw new AssertionError("UTF-8 file round trip");
                }
            }
            Future<Long> calculation = executor.submit(() -> 60_000L * 15);
            if (calculation.get() != 900_000L) {
                throw new AssertionError("Background calculation");
            }
            System.out.println("All Java file and executor checks passed");
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(path);
        }
    }
}
