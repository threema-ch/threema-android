package ch.threema.app.voip;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/**
 * An immutable snapshot of the call state.
 * <p>
 * Cannot be modified and is therefore tread safe.
 */
@AnyThread
public class CallStateSnapshot {
    private final @CallState.State int state;
    private final long callId;

    @Deprecated
    private final long incomingCallCounter;

    public CallStateSnapshot(int state, long callId, long incomingCallCounter) {
        this.state = state;
        this.callId = callId;
        this.incomingCallCounter = incomingCallCounter;
    }

    @Override
    public String toString() {
        return "CallState{" +
            "state=" + this.getName() +
            ", callId=" + this.callId +
            '}';
    }

    public boolean isIdle() {
        return this.state == CallState.IDLE;
    }

    public boolean isRinging() {
        return this.state == CallState.RINGING;
    }

    public boolean isInitializing() {
        return this.state == CallState.INITIALIZING;
    }

    public boolean isCalling() {
        return this.state == CallState.CALLING;
    }

    public boolean isDisconnecting() {
        return this.state == CallState.DISCONNECTING;
    }

    public long getCallId() {
        return this.callId;
    }

    @Deprecated
    public long getIncomingCallCounter() {
        return incomingCallCounter;
    }

    /**
     * Get the state name.
     */
    public @NonNull String getName() {
        return CallState.getStateName(this.state);
    }
}
