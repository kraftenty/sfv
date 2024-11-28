import checkout.CheckoutService;
import commit.CommitService;
import init.InitService;
import log.LogService;
import status.StatusService;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class CommandParser {

    private final InitService initService;
    private final CommitService commitService;
    private final CheckoutService checkoutService;
    private final StatusService statusService;
    private final LogService logService;

    public CommandParser() {
        this.initService = new InitService();
        this.commitService = new CommitService();
        this.checkoutService = new CheckoutService();
        this.statusService = new StatusService();
        this.logService = new LogService();
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
        System.out.println("  commit -m <message>      commit.Commit changes");
        System.out.println("  log                       View commit history");
        System.out.println("  status                    Check current status");
        System.out.println("  checkout <commit-id>     Checkout a specific commit");
    }

    public void handleInit() {
        try {
            initService.init();
            System.out.println("[init] repository initialized.");
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public void handleCommit(String[] args) {
        try {
            long start = System.currentTimeMillis();
            // commit -m "msg" 형식이 잘 맞춰졌는지 확인
            if (args.length < 3 || !args[1].equals("-m")) {
                System.out.println("usage: sfv commit -m \"commit message\"");
                return;
            }
            String message = args[2];
            commitService.commit(message);
            long end = System.currentTimeMillis();
            System.out.println("commit time : " + (end - start));
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        }
    }

    public void handleCheckout(String[] args) {
        try {
            long start = System.currentTimeMillis();
            if (args.length < 2) {
                System.out.println("Usage: sfv checkout <commit-id>");
                return;
            }
            String partialCommitId = args[1];
            checkoutService.checkout(partialCommitId);
            long end = System.currentTimeMillis();
            System.out.println("checkout time : " + (end - start));
        } catch (IOException e) {
            System.err.println("error occurred while checkout : " + e.getMessage());
        }
    }

    public void handleStatus() {
        try {
            long start = System.currentTimeMillis();
            statusService.getStatus();
            long end = System.currentTimeMillis();
            System.out.println("status time : " + (end - start));
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        }
    }

    public void handleLog() {
        try {
            logService.getLog();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

}
