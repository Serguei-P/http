package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

public class ChunkedOutputStream extends OutputStream {

    private static final String CODEPAGE = "ASCII";
    private static final byte[] CRLF = {13, 10};
    private static final byte[] LAST_CHUNK = {'0', 13, 10, 13, 10};
    private static final int DEFAULT_CHUNK_SIZE = 16384;

    private final OutputStream outputStream;
    private final byte[] chunkBuffer;
    private final boolean leaveUnderlyingStreamOpen;
    private int bytesInChunk = 0;

    public ChunkedOutputStream(OutputStream outputStream) {
        this(outputStream, DEFAULT_CHUNK_SIZE, false);
    }

    public ChunkedOutputStream(OutputStream outputStream, int chunkSize) {
        this(outputStream, chunkSize, false);
    }

    public ChunkedOutputStream(OutputStream outputStream, boolean leaveUnderlyingStreamOpen) {
        this(outputStream, DEFAULT_CHUNK_SIZE, leaveUnderlyingStreamOpen);
    }

    public ChunkedOutputStream(OutputStream outputStream, int chunkSize, boolean leaveUnderlyingStreamOpen) {
        this.outputStream = outputStream;
        this.chunkBuffer = new byte[chunkSize];
        this.leaveUnderlyingStreamOpen = leaveUnderlyingStreamOpen;
    }

    @Override
    public void write(int b) throws IOException {
        if (bytesInChunk >= chunkBuffer.length) {
            writeChunk();
        }
        chunkBuffer[bytesInChunk++] = (byte)b;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || off > b.length || len < 0 || off + len > b.length || off + len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        int left = len;
        while (left > chunkBuffer.length - bytesInChunk) {
            System.arraycopy(b, off + len - left, chunkBuffer, bytesInChunk, chunkBuffer.length - bytesInChunk);
            left -= chunkBuffer.length - bytesInChunk;
            bytesInChunk = chunkBuffer.length;
            writeChunk();
        }
        if (left > 0) {
            System.arraycopy(b, off + len - left, chunkBuffer, bytesInChunk, left);
            bytesInChunk += left;
        }
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        if (bytesInChunk > 0) {
            writeChunk();
        }
        outputStream.write(LAST_CHUNK);
        if (!leaveUnderlyingStreamOpen) {
            outputStream.close();
        }
    }

    private void writeChunk() throws IOException {
        outputStream.write(Integer.toHexString(bytesInChunk).getBytes(CODEPAGE));
        outputStream.write(CRLF);
        outputStream.write(chunkBuffer, 0, bytesInChunk);
        outputStream.write(CRLF);
        bytesInChunk = 0;
    }

}
