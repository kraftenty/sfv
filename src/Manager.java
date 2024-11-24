public class Manager {
    private static Manager instance;
    private final Core core;
    private final CommandParser commandParser;

    private Manager() {
        this.core = new Core();
        this.commandParser = new CommandParser(this.core);
    }

    public static Manager getInstance() {
        if (instance == null) {
            instance = new Manager();
        }
        return instance;
    }

    public Core getRepository() {
        return core;
    }

    public CommandParser getCommandParser() {
        return commandParser;
    }

}
