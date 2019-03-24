package serguei.http;

import java.net.Socket;

public interface HttpServerOnConnectProcess {

    /**
     * Process socket immediately after connection is made but after TLS handshake took place (if applicable)
     * 
     * @param socket
     *            - connection's socket
     * @param clientHello
     *            - information about protocol (if TLS), null if this is a plain connection
     * @return true if we would like to continue with this connection and false if we want to close it, there is no need
     *         to close the socket if returning false
     */
    public boolean process(Socket socket, ClientHello clientHello);
}
