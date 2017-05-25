package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

public interface HttpServerOnRequestHeadersProcess {

    /**
     * Processes request headers as soon as they are received
     * 
     * @param connectionContext
     *            - context of the client connection
     * @param request
     *            - an incoming request
     * @param outputStream
     *            - output stream where a response needs to be written, do not close this stream unless you wish to
     *            close the connection to the client. The client might make several requests on the same connection.
     * @throws IOException
     *             - thrown when connection is closed or the request does not follow HTTP standards
     * @return true if continue processing, false if not
     */
    public boolean process(ConnectionContext connectionContext, HttpRequestHeaders requestHeaders, OutputStream outputStream)
            throws IOException;
}
