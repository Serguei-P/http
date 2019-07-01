package serguei.http;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
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
    private final String sni;
    private final X509Certificate[] tlsCertificates;
    private final byte[] tlsSessionId;

    private CloseAction closeAction = CloseAction.NONE;

    ConnectionContext(Socket socket, ClientHello clientHello) {
        this.socket = socket;
        this.remoteSocketAddress = (InetSocketAddress)socket.getRemoteSocketAddress();
        if (socket instanceof SSLSocket) {
            ssl = true;
            SSLSocket sslSocket = (SSLSocket)socket;
            SSLSession sslSession = sslSocket.getSession();
            this.negotiatedTlsProtocol = TlsVersion.fromJdkString(sslSession.getProtocol());
            this.negotiatedCipher = sslSession.getCipherSuite();
            this.tlsSessionId = sslSession.getId();
            if (clientHello != null) {
                this.sni = clientHello.getSniHostName();
            } else {
                this.sni = "";
            }
            Certificate[] certificates;
            try {
                certificates = sslSession.getPeerCertificates();
            } catch (SSLPeerUnverifiedException e) {
                certificates = null;
            }
            if (certificates != null) {
                X509Certificate[] x509Certificates = new X509Certificate[certificates.length];
                for (int i = 0; i < certificates.length; i++) {
                    x509Certificates[i] = (X509Certificate)certificates[i];
                }
                tlsCertificates = x509Certificates;
            } else {
                tlsCertificates = null;
            }
        } else {
            ssl = false;
            this.negotiatedTlsProtocol = null;
            this.negotiatedCipher = null;
            this.sni = null;
            this.tlsCertificates = null;
            this.tlsSessionId = null;
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

    /**
     * @return A session id or null if TLS handshake did not happen
     */
    public byte[] getTlsSessionId() {
        return tlsSessionId;
    }

    /**
     * @return SNIs received from the client during TLS handshake
     */
    public String getSni() {
        return sni;
    }

    /**
     * @return list of certificates received from a client if client-side authentication was used, null if not TLS or if
     *         client did not send any certificates
     */
    public X509Certificate[] getTlsCertificates() {
        return tlsCertificates;
    }

    CloseAction getCloseAction() {
        return closeAction;
    }

}
