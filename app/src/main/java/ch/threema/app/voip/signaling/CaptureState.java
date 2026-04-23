package ch.threema.app.voip.signaling;

import com.google.protobuf.ByteString;

import androidx.annotation.NonNull;
import ch.threema.app.utils.RandomUtil;
import ch.threema.protobuf.o2o_call.CaptureState.CaptureDevice;
import ch.threema.protobuf.o2o_call.Envelope;

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
        return Envelope.CAPTURE_STATE_CHANGE_FIELD_NUMBER;
    }

    //endregion

    //region Protocol buffers

    @Override
    public @NonNull Envelope toSignalingMessage() {
        final ch.threema.protobuf.o2o_call.CaptureState.Builder captureState = ch.threema.protobuf.o2o_call.CaptureState.newBuilder()
            .setDevice(this.device)
            .setState(
                this.capturing
                    ? ch.threema.protobuf.o2o_call.CaptureState.Mode.ON
                    : ch.threema.protobuf.o2o_call.CaptureState.Mode.OFF
            );
        return Envelope.newBuilder()
            .setPadding(ByteString.copyFrom(RandomUtil.generateRandomPadding(0, 255)))
            .setCaptureStateChange(captureState)
            .build();
    }

    //endregion
}
