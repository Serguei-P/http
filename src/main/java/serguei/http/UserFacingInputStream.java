package serguei.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import serguei.http.utils.Utils;

class UserFacingInputStream extends FilterInputStream {

    private final InputStream streamToDrainOfData;
    private boolean eof;

    UserFacingInputStream(InputStream inputStream, InputStream streamToDrainOfData) {
        super(inputStream);
        this.streamToDrainOfData = streamToDrainOfData;
    }

    @Override
    public int read() throws IOException {
        int result = in.read();
        if (result == -1) {
            eof = true;
            close();
        }
        return result;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        int result = in.read(buffer, off, len);
        if (result == -1) {
            eof = true;
            close();
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        if (streamToDrainOfData != null) {
            Utils.readFully(streamToDrainOfData);
        } else if (!eof) {
            Utils.readFully(in);
            eof = true;
        }
    }
}
