package init;

import java.io.IOException;
import common.FileUtil;

public class InitService {

    public void init() throws IOException {
        FileUtil.initializeDotSfv();
        FileUtil.initializeHEAD();
    }

}
