import java.util.ArrayList;
import java.util.List;
// 1234556676743123421312321321123321123123312312321312312312
public class Main {
    public static void main(String[] args) {
        // 명령어 자동화를 위한 리스트
        List<String[]> commands = new ArrayList<>();
        commands.add(new String[]{"init"});
        commands.add(new String[]{"status"});
        commands.add(new String[]{"commit", "-m", "first commit"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"commit", "-m", "second commit"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"log"});
//        commands.add(new String[]{"commit", "-m", "444 commit"});
//        commands.add(new String[] {"checkout", "6dd9944691adb75f042376cf6e8551c1bdbc0b21"});

        // 각 명령어 실행
        for (String[] command : commands) {
            Manager.getInstance().getCommandParser().parseCommand(command);
        }
    }
}