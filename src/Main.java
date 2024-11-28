import util.FileUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 명령어 자동화를 위한 리스트
        List<String[]> commands = new ArrayList<>();
        commands.add(new String[]{"init"});
//        commands.add(new String[]{"status"});

        commands.add(new String[]{"commit", "-m", "first commit"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"commit", "-m", "second commit"});
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"log"});
//        commands.add(new String[]{"commit", "-m", "new commit"});
//        commands.add(new String[] {"checkout", "be75662847013f77ce099c50ad1bb7dbdb515b7e"});

        // 각 명령어 실행
        CommandParser commandParser = new CommandParser();
        for (String[] command : commands) {
            commandParser.parseCommand(command);
        }

//        try {
//            FileUtil.deleteSfvRepository();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}