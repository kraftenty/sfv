public class Main {
    public static void main(String[] args) {
        // 의존성 수동 주입
        Manager.getInstance().getCommandParser().parseCommand(args);
    }
}