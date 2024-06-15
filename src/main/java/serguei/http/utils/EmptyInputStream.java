package serguei.http.utils;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;

public class EmptyInputStream extends FilterInputStream {

    public EmptyInputStream() {
        super(new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public int read() throws IOException {
        return -1;
    }

    @Override
    public int read(byte[] buffer, int offset, int len) {
        return -1;
    }

    @Override
    public long skip(long n) {
        return 0;
    }
}
