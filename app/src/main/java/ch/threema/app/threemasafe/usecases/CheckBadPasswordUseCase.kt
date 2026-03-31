package ch.threema.app.threemasafe.usecases

import android.content.Context
import androidx.annotation.WorkerThread
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.base.utils.getThreemaLogger
import java.io.IOException

private val logger = getThreemaLogger("CheckBadPasswordUseCase")

class CheckBadPasswordUseCase(
    private val appContext: Context,
    private val appRestrictions: AppRestrictions,
) {
    /**
     * Checks whether the given password is considered bad
     */
    @WorkerThread
    fun call(password: String): Result {
        if (isBadPassword(password)) {
            return Result.BAD_PASSWORD
        }
        return Result.ACCEPTABLE_PASSWORD
    }

    private fun isBadPassword(password: String): Boolean {
        val pattern = appRestrictions.getSafePasswordPattern()
        if (pattern != null) {
            return !pattern.matcher(password).matches()
        }

        if (passwordChecks.any { regex -> password.matches(regex.toRegex()) }) {
            return true
        }

        try {
            appContext.assets.open("passwords/bad_passwords.txt").bufferedReader().useLines { lines ->
                if (lines.any { line -> password.equals(line, ignoreCase = true) }) {
                    return true
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to read bad passwords file", e)
        }
        return false
    }

    enum class Result {
        BAD_PASSWORD,
        ACCEPTABLE_PASSWORD,
    }

    companion object {
        private val passwordChecks = arrayOf(
            // do not allow single repeating characters
            "(.)\\1+",
            // do not allow short numeric-only passwords
            "^[0-9]{0,15}$",
        )
    }
}
