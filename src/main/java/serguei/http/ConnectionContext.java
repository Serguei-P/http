package serguei.http;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

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

    private CloseAction closeAction = CloseAction.NONE;

    ConnectionContext(Socket socket) {
        this.socket = socket;
        this.remoteSocketAddress = (InetSocketAddress)socket.getRemoteSocketAddress();
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

    CloseAction getCloseAction() {
        return closeAction;
    }

}
