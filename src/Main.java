import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        // 명령어 자동화를 위한 리스트
        List<String[]> commands = new ArrayList<>();


        commands.add(new String[]{"init"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"commit", "-m", "first commit"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"commit", "-m", "second commit"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"log"});


//        common.DummyFileGenerator generator = new common.DummyFileGenerator();
//        generator.generateFiles(50,50);

        // 각 명령어 실행
        CommandParser commandParser = new CommandParser();
        for (String[] command : commands) {
            commandParser.parseCommand(command);
        }
    }
}