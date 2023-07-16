package serguei.http.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MultipartInputStream extends InputStream {

    private final List<InputStreamProvider> streams;

    private InputStream currentStream;
    private int currentElement;

    public MultipartInputStream(List<InputStreamProvider> streams) throws IOException {
        this.streams = streams;
        if (!streams.isEmpty()) {
            currentStream = streams.get(0).getStream();
        }
    }


    @Override
    public int read() throws IOException {
        if (currentStream == null) {
            return -1;
        }
        while (true) {
            int value = currentStream.read();
            if (value != -1) {
                return value;
            }
            currentStream.close();
            currentElement++;
            if (currentElement >= streams.size()) {
                currentStream = null;
                return -1;
            }
            currentStream = streams.get(currentElement).getStream();
        }
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        if (currentStream == null) {
            return -1;
        }
        while (true) {
            int result = currentStream.read(buffer, off, len);
            if (result != -1) {
                return result;
            }
            currentStream.close();
            currentElement++;
            if (currentElement >= streams.size()) {
                currentStream = null;
                return -1;
            }
            currentStream = streams.get(currentElement).getStream();
        }
    }

    @Override
    public void close() throws IOException {
        if (currentStream != null) {
            currentStream.close();
            currentStream = null;
        }
    }
}
