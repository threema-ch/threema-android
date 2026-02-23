package ch.threema.app.webclient.services.instance.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.saltyrtc.client.SaltyRTCBuilder;

import ch.threema.app.webclient.state.WebClientSessionState;

/**
 * There was an error during handling of the session.
 */
@WorkerThread
final class SessionStateError extends SessionState {
    SessionStateError(@NonNull SessionContext ctx) {
        super(WebClientSessionState.ERROR, ctx);
        logger.info("Initializing with no connection");
    }

    SessionStateError(@NonNull SessionContext ctx, @Nullable SessionConnectionContext cctx) {
        super(WebClientSessionState.ERROR, ctx);
        logger.info("Initializing with existing connection");

        // Tear down the existing connection
        logger.info("Cleanup");
        CleanupHelper.cleanupSessionConnectionContext(logger, cctx);
        CleanupHelper.cleanupSessionContext(logger, this.ctx);
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
