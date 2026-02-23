package ch.threema.app.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Wraps an SSLSocketFactory, ensuring that TLSv1.2 (and v1.3) are always enabled if they are supported.
 */
public class TLSUpgradeSocketFactoryWrapper extends SSLSocketFactory {
    private SSLSocketFactory baseFactory;

    public TLSUpgradeSocketFactoryWrapper(SSLSocketFactory baseFactory) {
        this.baseFactory = baseFactory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return baseFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return baseFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return upgradeTLSOnSocket(baseFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return upgradeTLSOnSocket(baseFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return upgradeTLSOnSocket(baseFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return upgradeTLSOnSocket(baseFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return upgradeTLSOnSocket(baseFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return upgradeTLSOnSocket(baseFactory.createSocket(address, port, localAddress, localPort));
    }

    private Socket upgradeTLSOnSocket(Socket socket) {
        if (socket != null && (socket instanceof SSLSocket)) {
            String[] supportedProtocols = ((SSLSocket) socket).getSupportedProtocols();
            boolean supportsTLSv1_2 = false;
            boolean supportsTLSv1_3 = false;
            for (String protocol : supportedProtocols) {
                if (protocol.equals("TLSv1.2")) {
                    supportsTLSv1_2 = true;
                } else if (protocol.equals("TLSv1.3")) {
                    supportsTLSv1_3 = true;
                }
            }

            if (supportsTLSv1_3) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            } else if (supportsTLSv1_2) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2"});
            }
        }
        return socket;
    }
}
