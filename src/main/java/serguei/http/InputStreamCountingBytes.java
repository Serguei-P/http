package serguei.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

public class InputStreamCountingBytes extends FilterInputStream {

    private final AtomicLong counter;

    public InputStreamCountingBytes(InputStream in) {
        this(in, new AtomicLong());
    }

    public InputStreamCountingBytes(InputStream in, AtomicLong counter) {
        super(in);
        this.counter = counter;
    }

    @Override
    public int read() throws IOException {
        int result = in.read();
        if (result != -1) {
            counter.incrementAndGet();
        }
        return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int result = in.read(b);
        if (result != -1) {
            counter.addAndGet(result);
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = in.read(b, off, len);
        if (result != -1) {
            counter.addAndGet(result);
        }
        return result;
    }

    public long bytesRead() {
        return counter.get();
    }

}
