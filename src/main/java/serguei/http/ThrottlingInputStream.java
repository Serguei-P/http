package serguei.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ThrottlingInputStream extends FilterInputStream {

    private final int timeoutMils;
    private final int bytesBetweenDelays;
    private int count = 0;
    
    protected ThrottlingInputStream(InputStream in, int bytesBetweenDelays, int timeoutMils) {
        super(in);
        this.bytesBetweenDelays = bytesBetweenDelays;
        this.timeoutMils = timeoutMils;
    }

    public synchronized int read() throws IOException {
        if (count >= bytesBetweenDelays) {
            count -= bytesBetweenDelays;
            pause();
        }
        count++;
        return in.read();
    }

    public synchronized int read(byte b[], int off, int len) throws IOException {
        if (count >= bytesBetweenDelays) {
            count -= bytesBetweenDelays;
            pause();
        }
        if (count + len > bytesBetweenDelays) {
            len = bytesBetweenDelays - count;
            if (len <= 0) {
                len = 1;
            }
        }
        count += len;
        return in.read(b, off, len);
    }

    private void pause() {
        try {
            Thread.sleep(timeoutMils);
        } catch (InterruptedException e) {
            // nothing can be done
        }
    }

}
