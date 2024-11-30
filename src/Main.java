import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("[sfv] input command...");
            return;
        }

        CommandParser commandParser = new CommandParser();
        commandParser.parseCommand(args);
    }
}