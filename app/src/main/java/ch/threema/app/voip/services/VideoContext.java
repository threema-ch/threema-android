/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.voip.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.JavaI420Buffer;
import org.webrtc.NV21Buffer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Encapsulate information required for rendering video.
 *
 * Instances of this class live in `VoipStateService`.
 */
public class VideoContext {
	private static final Logger logger = LoggerFactory.getLogger(VideoContext.class);

	// Camera orientation for VideoContext
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({CAMERA_FRONT, CAMERA_BACK})
	public @interface CameraOrientation {}
	public final static int CAMERA_FRONT = 0;
	public final static int CAMERA_BACK = 1;

	// Local state
	private @Nullable EglBase eglBase;
	private @Nullable CameraVideoCapturer cameraVideoCapturer;
	private volatile String frontCameraName;
	private volatile String backCameraName;
	private @CameraOrientation int cameraOrientation;
	private @Nullable ProxyVideoSink localVideoSink;
	private @Nullable ProxyVideoSink remoteVideoSink;
	private int frameWidth, frameHeight;

	//region 0 Setup, teardown

	public VideoContext() {
		logger.trace("Constructor");
		this.eglBase = EglBase.create();
		this.localVideoSink = new ProxyVideoSink("Local");
		this.remoteVideoSink = new ProxyVideoSink("Remote");
	}

	/**
	 * Release resources associated with this context instance.
	 *
	 * After calling `release()`, the instance should not be used anymore.
	 * It's safe to call this method multiple times though.
	 */
	public void release() {
		if (this.localVideoSink != null) {
			this.localVideoSink.setTarget(null);
			this.localVideoSink = null;
		}

		if (this.remoteVideoSink != null) {
			this.remoteVideoSink.setTarget(null);
			this.remoteVideoSink = null;
		}

		if (this.eglBase != null) {
			this.eglBase.release();
			this.eglBase = null;
		}
	}

	//endregion

	//region 1 Getters and setters

	public @CameraOrientation int getCameraOrientation() {
		return cameraOrientation;
	}

	public void setCameraOrientation(@CameraOrientation int cameraOrientation) {
		this.cameraOrientation = cameraOrientation;
	}

	public @Nullable String getFrontCameraName() {
		return frontCameraName;
	}

	public @Nullable String getBackCameraName() {
		return backCameraName;
	}

	public void setFrontCameraName(@Nullable String frontCameraName) {
		this.frontCameraName = frontCameraName;
	}

	public void setBackCameraName(@Nullable String backCameraName) {
		this.backCameraName = backCameraName;
	}

	@NonNull
	public EglBase.Context getEglBaseContext() throws IllegalStateException {
		if (this.eglBase == null) {
			throw new IllegalStateException("VideoContext resources have already been released!");
		}
		return this.eglBase.getEglBaseContext();
	}

	public void setCameraVideoCapturer(@Nullable CameraVideoCapturer capturer) {
		this.cameraVideoCapturer = capturer;
	}

	@Nullable
	public CameraVideoCapturer getCameraVideoCapturer() {
		return this.cameraVideoCapturer;
	}

	public boolean hasMultipleCameras() {
		return frontCameraName != null && backCameraName != null;
	}

	/**
	 * Return the local video sink.
	 */
	@Nullable
	VideoSink getLocalVideoSinkProxy() {
		return this.localVideoSink;
	}

	/**
	 * Set or overwrite the local video sink target.
	 */
	public void setLocalVideoSinkTarget(@Nullable VideoSink videoSink) {
		logger.debug("Setting local video sink target: {}",
			videoSink != null ? videoSink.getClass().getName() : null);
		if (this.localVideoSink != null) {
			this.localVideoSink.setTarget(videoSink);
		}
	}

	/**
	 * Return the remote video sink.
	 */
	@Nullable
	VideoSink getRemoteVideoSinkProxy() {
		return this.remoteVideoSink;
	}

	/**
	 * Convert a byte array to a direct ByteBuffer.
	 * */
	private static ByteBuffer toByteBuffer(byte[] array) {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(array.length);
		buffer.put(array);
		buffer.rewind();
		return buffer;
	}

	/**
	 * Clear the remote video sink by sending a single black frame.
	 */
	public void clearRemoteVideoSinkProxy() {
		if (this.remoteVideoSink != null) {
			logger.trace("clearRemoteVideoSinkProxy");

			// A black 2x2 pixel YUV frame
			final ByteBuffer y = toByteBuffer(new byte[]{0, 0, 0, 0});
			final ByteBuffer u = toByteBuffer(new byte[]{(byte) 0x80});
			final ByteBuffer v = toByteBuffer(new byte[]{(byte) 0x80});
			final VideoFrame.I420Buffer i420Buffer = JavaI420Buffer.wrap(
				2, 2,
				y, 2,
				u, 2,
				v, 2,
				null
			);
			this.remoteVideoSink.onFrame(new VideoFrame(i420Buffer, 0, 0));
		}
	}

	/**
	 * Set or overwrite the remote video sink target.
	 */
	public void setRemoteVideoSinkTarget(@Nullable VideoSink videoSink) {
		logger.debug("Setting remote video sink target: {}",
			videoSink != null ? videoSink.getClass().getName() : null);
		if (this.remoteVideoSink != null) {
			this.remoteVideoSink.setTarget(videoSink);
		}
	}

	public void setFrameDimensions(int width, int height) {
		this.frameWidth = width;
		this.frameHeight = height;
	}

	public int getFrameWidth() {
		return frameWidth;
	}

	public int getFrameHeight() {
		return frameHeight;
	}

	//endregion

	//region Helper classes

	/**
	 * Proxy class that forwards video frames to a specified target.
	 * If no target is set using the `setTarget` method, drop frames.
	 */
	private static class ProxyVideoSink implements VideoSink {
		private static final Logger logger = LoggerFactory.getLogger(ProxyVideoSink.class);

		private @Nullable VideoSink target;
		private final @NonNull String label;

		public ProxyVideoSink(@NonNull String label) {
			this.label = label;
		}

		@Override
		public void onFrame(VideoFrame videoFrame) {
			// Note: The access to `target` is not synchronized here, because `onFrame` is called
			// many times per second. Synchronization is expensive in that case, while the
			// consequence of a data race would be a very small amount of dropped frames (not
			// noticeable to the user).
			//logger.trace(this.label + ": onFrame");
			if (this.target == null) {
				logger.trace("{}: Dropping frame in proxy because target is null", this.label);
				return;
			}
			target.onFrame(videoFrame);
		}

		synchronized public void setTarget(@Nullable VideoSink target) {
			this.target = target;
		}
	}

	//endregion
}
