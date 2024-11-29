import util.FileUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        // 명령어 자동화를 위한 리스트
        List<String[]> commands = new ArrayList<>();
        commands.add(new String[]{"init"});
        commands.add(new String[]{"commit", "-m", "first commit"});
//        commands.add(new String[]{"commit", "-m", "new commit"});
//        commands.add(new String[] {"checkout", "f894cf1e4c6b1c8146a938f04c3e09544d2cd96f"});

        // 각 명령어 실행
        CommandParser commandParser = new CommandParser();
        for (String[] command : commands) {
            commandParser.parseCommand(command);
        }

        try {
            FileUtil.deleteSfvRepository();
            System.out.println(".sfv file deleted");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}