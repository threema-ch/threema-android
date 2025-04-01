/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.protocol.connection.csp.socket;

import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.HttpProxySocket;

public class ProxyAwareSocketFactory {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ProxyAwareSocketFactory");

    public static boolean shouldUseProxy(String hostname, int port) {
        List<Proxy> proxies = ProxySelector.getDefault().select(URI.create("https://" + hostname + ":" + port + "/"));
        if (proxies.size() == 0 || proxies.get(0) == Proxy.NO_PROXY || proxies.get(0).type() == Proxy.Type.DIRECT) {
            return false;
        }
        return true;
    }

    @NonNull
    public static Socket makeSocket(InetSocketAddress address) {
        List<Proxy> proxies = ProxySelector.getDefault().select(URI.create("https://" + address.getHostName() + ":" + address.getPort() + "/"));
        if (proxies.size() == 0 || proxies.get(0) == Proxy.NO_PROXY || proxies.get(0).type() == Proxy.Type.DIRECT) {
            // No proxy
            logger.info("No proxy configured");
            return new Socket();
        }

        // Look for a SOCKS proxy first, as we prefer that
        Proxy chosenProxy = null;
        for (Proxy proxy : proxies) {
            if (proxy.type() == Proxy.Type.SOCKS) {
                chosenProxy = proxy;
                break;
            }
        }

        if (chosenProxy == null) {
            // Fall back to the first HTTP proxy
            for (Proxy proxy : proxies) {
                if (proxy.type() == Proxy.Type.HTTP) {
                    chosenProxy = proxy;
                    break;
                }
            }
        }

        if (chosenProxy == null) {
            logger.info("No proxy chosen");
            return new Socket();
        }

        // Check if the chosen proxy supports SOCKS or HTTP. For SOCKS, we can directly
        // create a Socket with the proxy configuration. For HTTP, we need to supply our own
        // implementation as JDK 7 does not support HTTP for Socket.
        logger.info("Using proxy: " + chosenProxy);
        switch (chosenProxy.type()) {
            case SOCKS:
                return new Socket(chosenProxy);
            case HTTP:
                return new HttpProxySocket(chosenProxy);
            default:
                // This should actually not be reachable as only Sockets with type SOCKS or HTTP are
                // considered.
                throw new RuntimeException("Invalid proxy type '" + chosenProxy.type() + "' set");
        }
    }
}
