public class Manager {
    private static Manager instance;
    private final Repository repository;
    private final CommandParser commandParser;
    private final ParallelStrategy parallelStrategy;

    private Manager() {
        this.parallelStrategy = new ParallelStrategyImplV1();
        this.repository = new Repository(this.parallelStrategy);
        this.commandParser = new CommandParser(this.repository);
    }

    public static Manager getInstance() {
        if (instance == null) {
            instance = new Manager();
        }
        return instance;
    }

    public Repository getRepository() {
        return repository;
    }

    public CommandParser getCommandParser() {
        return commandParser;
    }

    public ParallelStrategy getParallelStrategy() {
        return parallelStrategy;
    }
}
