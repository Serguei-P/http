package serguei.http.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * This is a version of ByteArrayInputStream that does not return end of file when data runs out but makes the caller
 * wait. New data can be pushed into the stream
 * 
 * @author Serguei Poliakov
 *
 */
public class WaitingByteArrayInputStream extends ByteArrayInputStream {

    public WaitingByteArrayInputStream(byte[] data) {
        super(data);
    }

    public WaitingByteArrayInputStream() {
        super(new byte[0]);
    }

    @Override
    public synchronized int read() {
        while (available() == 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }
        return super.read();
    }

    @Override
    public synchronized int read(byte[] buffer, int off, int len) {
        while (available() == 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }
        return super.read(buffer, off, len);
    }

    /**
     * Pushes a string for the other process to read
     * 
     * @param line
     *            - string to push for the other process to read
     * @throws IOException
     */
    public synchronized void push(String line) throws IOException {
        buf = Utils.concat(buf, line.getBytes("UTF-8"));
        count = buf.length;
        this.notifyAll();
    }

}
