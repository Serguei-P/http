package serguei.http;

import java.io.IOException;
import java.io.InputStream;

public class LimitedLengthInputStream extends InputStream {

    private final InputStream inputStream;
    private final long maxLength;
    private long totalRead = 0;

    LimitedLengthInputStream(InputStream inputStream, long maxLength) {
        this.inputStream = inputStream;
        this.maxLength = maxLength;
    }

    @Override
    public int read() throws IOException {
        if (totalRead >= maxLength) {
            return -1;
        }
        int result = inputStream.read();
        if (result != -1) {
            totalRead++;
        }
        return result;
    }

    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (totalRead >= maxLength) {
            return -1;
        }
        long length = len > (maxLength - totalRead) ? maxLength - totalRead : len;
        int read = inputStream.read(b, off, (int)length);
        if (read > 0) {
            totalRead += read;
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        if (totalRead >= maxLength) {
            return -1;
        }
        long skipped = inputStream.skip(n);
        if (skipped > 0) {
            totalRead += skipped;
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        int available = inputStream.available();
        if (totalRead + available > maxLength) {
            available = (int)(maxLength - totalRead);
        }
        return available;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    @Override
    public void mark(int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
