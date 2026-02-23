package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import java.lang.Exception

class OnPremConfigParseException(cause: Exception) : ThreemaException("Failed to parse OnPrem config", cause)
