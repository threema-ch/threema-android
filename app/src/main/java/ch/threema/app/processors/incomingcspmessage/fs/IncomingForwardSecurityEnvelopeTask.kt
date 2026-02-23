package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.app.tasks.ActiveComposableTask
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult

interface IncomingForwardSecurityEnvelopeTask :
    ActiveComposableTask<ForwardSecurityDecryptionResult>
