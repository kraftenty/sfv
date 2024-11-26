package init;

import java.io.IOException;
import common.FileUtil;

public class InitService {

    private final FileUtil fileUtil;

    public InitService() {
        fileUtil = new FileUtil();
    }

    public void init() throws IOException {
        fileUtil.initializeDotSfv();
        fileUtil.initializeHEAD();
    }

}
