package ch.threema.app.rating

import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.generateRandomBytes
import ch.threema.common.secureRandom
import ch.threema.common.toHexString
import java.security.SecureRandom

class RatingReferenceProvider
@JvmOverloads
constructor(
    private val preferenceService: PreferenceService,
    private val secureRandom: SecureRandom = secureRandom(),
) {
    fun getOrCreateRatingReference(): String {
        var reference = preferenceService.ratingReference
        if (reference.isNullOrEmpty()) {
            reference = generateRatingReference()
            preferenceService.ratingReference = reference
        }
        return reference
    }

    private fun generateRatingReference(): String =
        secureRandom.generateRandomBytes(32).toHexString()
}
