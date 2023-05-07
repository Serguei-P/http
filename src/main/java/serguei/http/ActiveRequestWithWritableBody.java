package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This is an unfinished client request on a connection.
 * An instance is created after the request headers were sent to the server but the request body was not
 * The purpose of this is to be able to drip-feed the data into the body as the data arrives from elsewhere
 */
public class ActiveRequestWithWritableBody {

    private final OutputStream bodyStream;
    private final InputStream inputStream;

    ActiveRequestWithWritableBody(OutputStream bodyStream, InputStream inputStream) {
        this.bodyStream = bodyStream;
        this.inputStream = inputStream;
    }

    /**
     * This writes part of request body into a socket
     * @param buffer - buffer with data
     * @throws IOException
     */
    public void write(byte[] buffer) throws IOException {
        bodyStream.write(buffer, 0, buffer.length);
    }

    /**
     * This writes part of request body into a socket
     * @param buffer - buffer with data
     * @param offset - data offset in buffer
     * @param len - length of the data
     * @throws IOException
     */
    public void write(byte[] buffer, int offset, int len) throws IOException {
        bodyStream.write(buffer, offset, len);
    }

    /**
     * This finishes request body (e.g. writes final chunk) and waits until it reads response headers from the server
     * @return Server response
     * @throws IOException
     */
    public HttpResponse readResponse() throws IOException {
        bodyStream.close();
        return new HttpResponse(inputStream);
    }
}
