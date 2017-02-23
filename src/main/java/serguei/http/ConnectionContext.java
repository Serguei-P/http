package serguei.http;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

/**
 * Context of the client's TCP connection from the point of view of the server
 * 
 * @author Serguei Poliakov
 *
 */
public class ConnectionContext {

    enum CloseAction {
        NONE, CLOSE, RESET
    }

    private final Socket socket;
    private final InetSocketAddress remoteSocketAddress;
    private final boolean ssl;
    private final TlsVersion negotiatedTlsProtocol;
    private final String negotiatedCipher;

    private CloseAction closeAction = CloseAction.NONE;

    ConnectionContext(Socket socket) {
        this.socket = socket;
        this.remoteSocketAddress = (InetSocketAddress)socket.getRemoteSocketAddress();
        if (socket instanceof SSLSocket) {
            ssl = true;
            SSLSocket sslSocket = (SSLSocket)socket;
            this.negotiatedTlsProtocol = TlsVersion.fromJdkString(sslSocket.getSession().getProtocol());
            this.negotiatedCipher = sslSocket.getSession().getCipherSuite();
        } else {
            ssl = false;
            this.negotiatedTlsProtocol = null;
            this.negotiatedCipher = null;
        }
    }

    Socket getSocket() {
        return socket;
    }

    /**
     * @return IP address of the remote client
     */
    public InetAddress getRemoteAddress() {
        return remoteSocketAddress.getAddress();
    }

    /**
     * Close connection to the client (sends FIN)
     */
    public void closeConnection() {
        closeAction = CloseAction.CLOSE;
    }

    /**
     * Reset connection to the client (sends FIN)
     */
    public void resetConnection() {
        closeAction = CloseAction.RESET;
    }

    /**
     * @return true if this is an SSL connection or false if it is plain connection
     */
    public boolean isSsl() {
        return ssl;
    }

    /**
     * @return A TLS protocol negotiated during TLS handshake or null if TLS handshake did not happen
     */
    public TlsVersion getNegotiatedTlsProtocol() {
        return negotiatedTlsProtocol;
    }

    /**
     * @return A TLS cipher negotiated during TLS handshake or null if TLS handshake did not happen
     */
    public String getNegotiatedCipher() {
        return negotiatedCipher;
    }

    CloseAction getCloseAction() {
        return closeAction;
    }

}
