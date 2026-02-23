package ch.threema.app.logging

sealed class AppVersionCheckResult {
    data object NoPreviousVersion : AppVersionCheckResult()
    data object SameVersion : AppVersionCheckResult()
    data class DifferentVersion(val previous: AppVersionRecord) : AppVersionCheckResult()
}
