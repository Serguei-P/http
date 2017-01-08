package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This is request handler. The job of implementation of this interface is to process one request
 * 
 * @author Serguei Poliakov
 *
 */
public interface HttpServerRequestHandler {

    /**
     * Processes one HTTP request
     * 
     * @param request
     *            - an incoming request
     * @param outputStream
     *            - output stream where a response needs to be written, do not close this stream unless you want to
     *            close the connection to the client. The client might make several requests on the same connection.
     * @throws IOException
     *             - thrown when connection is closed or the request does not follow HTTP standards
     */
    public void process(HttpRequest request, OutputStream outputStream) throws IOException;

}
