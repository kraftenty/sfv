package init;

import util.FileUtil;

import java.io.IOException;

public class InitService {

    public void init() throws IOException {
        FileUtil.initializeDotSfvDirectory();
    }

}
