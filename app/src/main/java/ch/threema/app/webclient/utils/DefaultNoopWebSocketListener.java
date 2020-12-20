/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

package ch.threema.app.webclient.utils;

import androidx.annotation.AnyThread;

import com.neovisionaries.ws.client.ThreadType;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import java.util.List;
import java.util.Map;

/**
 * A WebSocketListener that by default does not do anything (default methods everywhere).
 */
@AnyThread
public interface DefaultNoopWebSocketListener extends WebSocketListener {
	@Override
	default void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {}

	@Override
	default void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {}

	@Override
	default void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {}

	@Override
	default void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {}

	@Override
	default void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onTextMessage(WebSocket websocket, String text) throws Exception {}

	@Override
	default void onTextMessage(WebSocket websocket, byte[] data) throws Exception {}

	@Override
	default void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {}

	@Override
	default void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {}

	@Override
	default void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {}

	@Override
	default void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {}

	@Override
	default void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {}

	@Override
	default void onError(WebSocket websocket, WebSocketException cause) throws Exception {}

	@Override
	default void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {}

	@Override
	default void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {}

	@Override
	default void onMessageDecompressionError(WebSocket websocket, WebSocketException cause, byte[] compressed) throws Exception {}

	@Override
	default void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {}

	@Override
	default void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {}

	@Override
	default void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {}

	@Override
	default void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {}

	@Override
	default void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]> headers) throws Exception {}
}
