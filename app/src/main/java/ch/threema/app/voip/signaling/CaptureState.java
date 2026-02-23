package ch.threema.app.voip.signaling;

import com.google.protobuf.ByteString;

import androidx.annotation.NonNull;
import ch.threema.app.utils.RandomUtil;
import ch.threema.protobuf.callsignaling.O2OCall;
import ch.threema.protobuf.callsignaling.O2OCall.CaptureState.CaptureDevice;
import ch.threema.protobuf.callsignaling.O2OCall.CaptureState.Mode;

/**
 * Hold information about the capturing state for a certain device.
 */
public class CaptureState implements ToSignalingMessage {
    private final boolean capturing;
    private final @NonNull CaptureDevice device;

    private CaptureState(boolean capturing, @NonNull CaptureDevice device) {
        this.capturing = capturing;
        this.device = device;
    }

    public static @NonNull CaptureState microphone(boolean capturing) {
        return new CaptureState(capturing, CaptureDevice.MICROPHONE);
    }

    public static @NonNull CaptureState camera(boolean capturing) {
        return new CaptureState(capturing, CaptureDevice.CAMERA);
    }

    //region Getters

    @Override
    public int getType() {
        return O2OCall.Envelope.CAPTURE_STATE_CHANGE_FIELD_NUMBER;
    }

    //endregion

    //region Protocol buffers

    @Override
    public @NonNull O2OCall.Envelope toSignalingMessage() {
        final O2OCall.CaptureState.Builder captureState = O2OCall.CaptureState.newBuilder()
            .setDevice(this.device)
            .setState(this.capturing ? Mode.ON : Mode.OFF);
        return O2OCall.Envelope.newBuilder()
            .setPadding(ByteString.copyFrom(RandomUtil.generateRandomPadding(0, 255)))
            .setCaptureStateChange(captureState)
            .build();
    }

    //endregion
}
