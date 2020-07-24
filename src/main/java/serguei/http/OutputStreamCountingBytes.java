package serguei.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

public class OutputStreamCountingBytes extends FilterOutputStream {

    private final AtomicLong counter;

    public OutputStreamCountingBytes(OutputStream out) {
        this(out, new AtomicLong());
    }

    public OutputStreamCountingBytes(OutputStream out, AtomicLong counter) {
        super(out);
        this.counter = counter;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        counter.incrementAndGet();
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
        counter.addAndGet(b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        counter.addAndGet(len);
    }

    public long bytesWritten() {
        return counter.get();
    }

}
