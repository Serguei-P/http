package serguei.http;

import java.io.IOException;
import java.io.InputStream;

// TODO: read buffer functionality as optimisation
class ChunkedInputStream extends InputStream {

    private static int DEFAULT_BUFFER_SIZE = 8192;

    private final InputStream inputStream;
    private final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

    private boolean eof = false;
    private int bytesInBuffer;
    private int bufferPos = 0;
    private int leftInChunk = 0;
    private int chunkCount = 0;

    ChunkedInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (leftInChunk <= 0) {
            findChunk();
            if (leftInChunk <= 0) {
                return -1;
            }
        }
        leftInChunk--;
        return nextByte();
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        // TODO: to write own implementation for performance
        return super.read(b, off, len);
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

    private void findChunk() throws IOException {
        if (chunkCount > 0) {
            readCrLf();
        }
        leftInChunk = 0;
        int value;
        while ((value = nextByte()) >= 0) {
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
        while (!eof && value != 10) {
            // TODO: for now ignoring chunk extensions
            value = nextByte();
        }
        chunkCount++;
        if (leftInChunk == 0) {
            processEndOfStream();
        }
    }

    private int nextByte() throws IOException {
        if (!eof && bufferPos >= bytesInBuffer) {
            readNextBuffer();
        }
        if (!eof && bufferPos < bytesInBuffer) {
            return buffer[bufferPos++] & 0xff;
        } else {
            return -1;
        }
    }

    private void readNextBuffer() throws IOException {
        bytesInBuffer = inputStream.read(buffer);
        bufferPos = 0;
        if (bytesInBuffer <= 0) {
            eof = true;
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
        // TODO: for now ignoring trailer
        boolean lineStarted = false;
        while (!eof) {
            int value = nextByte();
            if (value == 13) {

            } else if (value == 10) {
                if (!lineStarted) {
                    eof = true;
                    return;
                }
                lineStarted = false;
            } else {
                lineStarted = true;
            }
        }
    }

    private void readCrLf() throws IOException {
        int value = nextByte();
        if (value != 13) {
            throw new IOException("Error in chunk encoding, expected \\r was " + value + " '" + (char)value + "'");
        }
        value = nextByte();
        if (value != 10) {
            throw new IOException("Error in chunk encoding, expected \\n was " + value + " '" + (char)value + "'");
        }
    }

}
