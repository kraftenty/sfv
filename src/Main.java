import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        // 명령어 자동화를 위한 리스트
        List<String[]> commands = new ArrayList<>();
        commands.add(new String[]{"init"});
        commands.add(new String[]{"status"});
        commands.add(new String[]{"commit", "-m", "first commit"});
        commands.add(new String[]{"status"});
//        //
//        commands.add(new String[]{"status"});
//        commands.add(new String[]{"commit", "-m", "second commit"});
//        commands.add(new String[]{"status"});
        //더미 파일 생성성
//        DummyFileGenerator generator = new DummyFileGenerator();
//        generator.generateFiles(50,50);
//
        commands.add(new String[]{"log"});

        // 각 명령어 실행
        for (String[] command : commands) {
            Manager.getInstance().getCommandParser().parseCommand(command);
            System.out.println("---------------------------------------------------");
        }
    }
}