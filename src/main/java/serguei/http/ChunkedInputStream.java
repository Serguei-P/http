package serguei.http;

import java.io.IOException;
import java.io.InputStream;

class ChunkedInputStream extends InputStream {

    private final InputStream inputStream;

    private int leftInChunk = 0;
    private int chunkCount = 0;
    private Trailer trailer = new Trailer();

    ChunkedInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        if (leftInChunk <= 0) {
            findChunk();
            if (leftInChunk <= 0) {
                return -1;
            }
        }
        leftInChunk--;
        return inputStream.read();
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        if (leftInChunk <= 0) {
            findChunk();
            if (leftInChunk <= 0) {
                return -1;
            }
        }
        int toRead = len > leftInChunk ? leftInChunk : len;
        int result = inputStream.read(b, 0, toRead);
        if (result >= 0) {
            leftInChunk -= result;
        }
        return result;
    }

    @Override
    public int available() throws IOException {
        return 0;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    /**
     * This returns a header from the trailer by name, if there are more then one header with this name, the first one
     * will be returned.
     * 
     * Returns null if header does not exit
     */
    public String getTrailerValue(String name) {
        return trailer.getHeader(name);
    }

    private void findChunk() throws IOException {
        if (chunkCount > 0) {
            readCrLf();
        }
        leftInChunk = 0;
        int value;
        while ((value = inputStream.read()) >= 0) {
            int digit = getDigit(value);
            if (digit >= 0) {
                if (leftInChunk > 0xfffffff) {
                    throw new IOException("Chunk size is too large");
                }
                leftInChunk = leftInChunk * 16 + digit;
            } else {
                break;
            }
        }
        while (value != 10) {
            // TODO: for now ignoring chunk extensions
            value = inputStream.read();
            if (value == -1) {
                leftInChunk = 0;
                return;
            }
        }
        chunkCount++;
        if (leftInChunk == 0) {
            processEndOfStream();
        }
    }

    private int getDigit(int value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }

    private void processEndOfStream() throws IOException {
        try {
            trailer.readHeaders(new HeaderLineReader(inputStream));
        } catch (IOException e) {
            throw new IOException("Error in chunk encoding while reading trailer", e);
        }
        if (!trailer.isEmpty()) {
            readCrLf();
        }
    }

    private void readCrLf() throws IOException {
        int value = inputStream.read();
        if (value != 13) {
            throw new IOException("Error in chunk encoding, expected \\r was " + value + " '" + (char)value + "'");
        }
        value = inputStream.read();
        if (value != 10) {
            throw new IOException("Error in chunk encoding, expected \\n was " + value + " '" + (char)value + "'");
        }
    }

    private class Trailer extends HttpHeaders {

    }

}
