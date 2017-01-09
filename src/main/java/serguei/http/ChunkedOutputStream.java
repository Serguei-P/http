package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream that produces chunked transfer encoded output.
 * 
 * Depending on creation parameters, the underlying stream might be left open after calling close() method of this
 * class. This is to allow, in case this stream was in turn wrapped with a different stream to be left open even when
 * close() was called (e.g. we have to call close() on GZIPOutputStream to finish off the process while we might want a
 * connection to stay open).
 * 
 * @author Serguei Poliakov
 *
 */
public class ChunkedOutputStream extends OutputStream {

    private static final String CODEPAGE = "ASCII";
    private static final byte[] CRLF = {13, 10};
    private static final byte[] LAST_CHUNK = {'0', 13, 10, 13, 10};
    private static final int DEFAULT_CHUNK_SIZE = 16384;

    private final OutputStream outputStream;
    private final byte[] chunkBuffer;
    private final boolean leaveUnderlyingStreamOpen;
    private int bytesInChunk = 0;

    /**
     * Create a new instance of ChunkedOutputStream by wrapping around OutputStream
     * 
     * The underlying output stream will be left open if we call close() on this instance
     * 
     * @param outputStream
     *            - stream to wrap
     */
    public ChunkedOutputStream(OutputStream outputStream) {
        this(outputStream, DEFAULT_CHUNK_SIZE, true);
    }

    /**
     * Create a new instance of ChunkedOutputStream by wrapping around OutputStream
     * 
     * The underlying output stream will be left open if we call close() on this instance
     * 
     * @param outputStream
     *            - stream to wrap
     * @param chunkSize
     *            - size of a chunk
     */
    public ChunkedOutputStream(OutputStream outputStream, int chunkSize) {
        this(outputStream, chunkSize, true);
    }

    /**
     * Create a new instance of ChunkedOutputStream by wrapping around OutputStream
     * 
     * @param outputStream
     *            - stream to wrap
     * @param leaveUnderlyingStreamOpen
     *            - if true when close() is called the outputStream is flushed, but not closed
     */
    public ChunkedOutputStream(OutputStream outputStream, boolean leaveUnderlyingStreamOpen) {
        this(outputStream, DEFAULT_CHUNK_SIZE, leaveUnderlyingStreamOpen);
    }

    /**
     * Create a new instance of ChunkedOutputStream by wrapping around OutputStream
     * 
     * @param outputStream
     *            - stream to wrap
     * @param chunkSize
     *            - size of a chunk
     * @param leaveUnderlyingStreamOpen
     *            - if true when close() is called the outputStream is flushed, but not closed
     */
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

    /**
     * This finishes write by writing last chunk and then, if the instance was created with parameter
     * leaveUnderlyingStreamOpen set to false, closes the underlying stream, otherwise it flushes it and leave opened
     */
    @Override
    public void close() throws IOException {
        if (bytesInChunk > 0) {
            writeChunk();
        }
        outputStream.write(LAST_CHUNK);
        if (leaveUnderlyingStreamOpen) {
            outputStream.flush();
        } else {
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
