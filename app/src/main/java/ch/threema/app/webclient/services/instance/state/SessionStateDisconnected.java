package ch.threema.app.webclient.services.instance.state;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.saltyrtc.client.SaltyRTCBuilder;

import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;

/**
 * The session is disconnected.
 */
@WorkerThread
final class SessionStateDisconnected extends SessionState {
    @AnyThread
    SessionStateDisconnected(
        @NonNull final SessionContext ctx
    ) {
        super(WebClientSessionState.DISCONNECTED, ctx);
        logger.info("Initializing with no connection");
    }

    SessionStateDisconnected(
        @NonNull final SessionContext ctx,
        @NonNull final SessionConnectionContext cctx,
        @NonNull final DisconnectContext reason
    ) {
        super(WebClientSessionState.DISCONNECTED, ctx);
        logger.info("Initializing with existing connection, reason: {}", reason);

        // Tear down the existing connection
        logger.info("Cleanup");
        CleanupHelper.cleanupSessionConnectionContext(logger, cctx);
        CleanupHelper.cleanupSessionContext(logger, this.ctx);
    }

    @Override
    @NonNull
    SessionStateDisconnected setDisconnected(@NonNull final DisconnectContext reason)
        throws IgnoredStateTransition {
        throw new IgnoredStateTransition("Already disconnected");
    }

    @Override
    @NonNull
    SessionStateConnecting setConnecting(@NonNull final SaltyRTCBuilder builder, @Nullable final String affiliationId)
        throws InvalidStateTransition {
        logger.info("Connecting");
        return new SessionStateConnecting(this.ctx, builder, affiliationId);
    }

    @Override
    @NonNull
    SessionStateError setError(@NonNull final String reason) {
        logger.error("Error: {}", reason);
        return new SessionStateError(this.ctx);
    }
}
