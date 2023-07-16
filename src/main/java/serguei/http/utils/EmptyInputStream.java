package serguei.http.utils;

import java.io.IOException;
import java.io.InputStream;

public class EmptyInputStream extends InputStream {

    @Override
    public int read() throws IOException {
        return -1;
    }

    public int read(byte[] buffer, int offset, int len) {
        return -1;
    }

    public long skip(long n) {
        return 0;
    }
}
