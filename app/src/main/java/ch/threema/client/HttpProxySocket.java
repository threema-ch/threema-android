/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HttpProxySocket extends Socket {

	private Proxy httpProxy = null;

	public HttpProxySocket(Proxy proxy) {
		super(proxy.type() == Proxy.Type.HTTP ? Proxy.NO_PROXY : proxy);
		if (proxy.type() == Proxy.Type.HTTP) {
			this.httpProxy = proxy;
		}
	}

	@Override
	public void connect(SocketAddress endpoint, int timeout) throws IOException {
		if (httpProxy != null) {
			connectHttpProxy(endpoint, timeout);
		} else {
			super.connect(endpoint, timeout);
		}
	}

	private void connectHttpProxy(SocketAddress endpoint, int timeout) throws IOException {
		SocketAddress proxyAddress = httpProxy.address();
		if (proxyAddress instanceof InetSocketAddress) {
			// Resolve InetSocketAddress if needed to avoid UnknownHostException
			if (((InetSocketAddress)proxyAddress).getAddress() == null) {
				proxyAddress = new InetSocketAddress(((InetSocketAddress) proxyAddress).getHostName(), ((InetSocketAddress) proxyAddress).getPort());
			}
		}
		super.connect(proxyAddress, timeout);

		if (!(endpoint instanceof InetSocketAddress)) {
			throw new SocketException("Endpoint is not an InetSocketAddress: " + endpoint);
		}
		InetSocketAddress isa = (InetSocketAddress) endpoint;
		String httpConnect = "CONNECT " + isa.getHostName() + ":" + isa.getPort() + " HTTP/1.0\r\n\r\n";
		getOutputStream().write(httpConnect.getBytes(StandardCharsets.UTF_8));
		checkAndFlushProxyResponse();
	}

	private void checkAndFlushProxyResponse() throws IOException {
		InputStream socketInput = getInputStream();
		byte[] tmpBuffer = new byte[512];
		int len = socketInput.read(tmpBuffer, 0, tmpBuffer.length);

		if (len == 0) {
			throw new SocketException("Proxy did not return response");
		}

		String proxyResponse = new String(tmpBuffer, 0, len, StandardCharsets.UTF_8);

		// 200 response expected
		if (proxyResponse.contains(" 200 ")) {
			if (socketInput.available() > 0) {
				socketInput.skip(socketInput.available());
			}
		} else {
			throw new SocketException("Bad response from proxy: " + proxyResponse);
		}
	}
}
