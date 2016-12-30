package serguei.http.examples;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class ServerWithPostFormTest {

    private final WaitingByteArrayInputStream inputStream = new WaitingByteArrayInputStream();
    private final ServerWithPostForm server = new ServerWithPostForm(inputStream);

    @Test
    public void shouldStopServer() throws Exception {
        Thread thread = new Thread(server);
        thread.start();
        waitUntilRunning(true);
        assertTrue(server.isRunning());

        inputStream.push("quit" + System.lineSeparator());

        waitUntilRunning(false);
        assertFalse(server.isRunning());
    }

    private void waitUntilRunning(boolean running) {
        long startTime = System.currentTimeMillis();
        while (server.isRunning() != running || System.currentTimeMillis() - startTime > 2000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private class WaitingByteArrayInputStream extends ByteArrayInputStream {

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

        private synchronized void push(String line) throws IOException {
            byte[] data = line.getBytes("UTF-8");
            byte[] newBuffer = new byte[buf.length + data.length];
            System.arraycopy(buf, 0, newBuffer, 0, buf.length);
            System.arraycopy(data, 0, newBuffer, buf.length, data.length);
            buf = newBuffer;
            count += data.length;
            this.notifyAll();
        }

    }

}
