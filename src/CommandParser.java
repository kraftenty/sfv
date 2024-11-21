import java.io.IOException;
import java.nio.file.FileSystemException;
import java.security.NoSuchAlgorithmException;

public class CommandParser {

    private final Repository repository;

    public CommandParser(Repository repository) {
        this.repository = repository;
    }

    public void parseCommand(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "init" -> handleInit();
            case "commit" -> handleCommit(args);
            case "checkout" -> handleCheckout(args);
            case "status" -> handleStatus();
            case "log" -> handleLog();
            default -> printUsage();
        }
    }

    public void printUsage() {
        System.out.println("Usage: sfv <command> [options]");
        System.out.println("Available commands:");
        System.out.println("  init                      Initialize a new repository");
        System.out.println("  commit -m <message>      Commit changes");
        System.out.println("  log                       View commit history");
        System.out.println("  status                    Check current status");
        System.out.println("  checkout <commit-id>     Checkout a specific commit");
    }

    public void handleInit() {
        try {
            repository.init();
            System.out.println("Repository initialized.");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void handleCommit(String[] args) {
        try {
            // commit -m "msg" 형식이 잘 맞춰졌는지 확인
            if (args.length < 3 || !args[1].equals("-m")) {
                System.out.println("Usage: sfv commit -m \"commit message\"");
                return;
            }
            String message = args[2];
            String commitId = repository.commit(message);
            System.out.println("[" + commitId.substring(0, 7) + "] " + message);
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }
    }

    public void handleCheckout(String[] args) {

    }

    public void handleStatus() {

    }

    public void handleLog() {

    }

}
