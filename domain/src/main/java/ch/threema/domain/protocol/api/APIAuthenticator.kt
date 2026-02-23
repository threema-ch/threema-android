package ch.threema.domain.protocol.api

import ch.threema.domain.models.UserCredentials

fun interface APIAuthenticator {
    fun getCredentials(): UserCredentials?
}
