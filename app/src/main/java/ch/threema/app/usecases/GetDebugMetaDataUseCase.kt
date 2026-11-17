package ch.threema.app.usecases

import android.os.Build
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.errorreporting.SentryIdProvider
import ch.threema.app.logging.AppVersionHistoryManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.base.HAS_DEV_FEATURES
import ch.threema.common.TimeProvider
import ch.threema.data.IdentityProvider
import ch.threema.localcrypto.MasterKeyManager
import java.util.Locale
import org.koin.core.component.KoinComponent

/**
 * Compiles metadata about the app or device which can be useful for debugging when shared with Threema support or developers.
 * New entries should be added sparingly and with much consideration, to not impact the user's privacy.
 * No personal information such as names, email addresses, message contents, location data, IP addresses, etc. must ever be included.
 */
class GetDebugMetaDataUseCase(
    private val identityProvider: IdentityProvider,
    private val masterKeyManager: MasterKeyManager,
    private val appVersionHistoryManager: AppVersionHistoryManager,
    private val sentryIdProvider: SentryIdProvider,
    private val appRestrictions: AppRestrictions,
    private val appRestrictionService: AppRestrictionService,
    private val preferenceService: PreferenceService,
    private val timeProvider: TimeProvider,
) : KoinComponent {
    private val multiDeviceManager: MultiDeviceManager? by injectNullableNonBinding()

    fun call(): String = buildString {
        keyValuePair("created", timeProvider.get())
        appendDeviceInfo()
        appendAppInfo()
        appendAppConfig()
        appendRestrictions(appRestrictionService)
        appendVersionHistory()
    }

    private fun StringBuilder.appendDeviceInfo() {
        section("device") {
            keyValuePair("android version", Build.VERSION.RELEASE)
            keyValuePair("manufacturer", Build.MANUFACTURER)
            keyValuePair("model", Build.MODEL)
        }
    }

    private fun StringBuilder.appendAppInfo() {
        section("app") {
            keyValuePair("app version", BuildConfig.VERSION_NAME)
            keyValuePair("app version code", BuildConfig.DEFAULT_VERSION_CODE)
            keyValuePair("build flavor", BuildFlavor.fullDisplayName)
            if (HAS_DEV_FEATURES) {
                keyValuePair("git commit", BuildConfig.GIT_HASH)
                keyValuePair("git branch", BuildConfig.GIT_BRANCH)
            }
            sentryIdProvider.getSentryId()?.let { sentryId ->
                keyValuePair("sentry id", sentryId)
            }
        }
    }

    private fun StringBuilder.appendAppConfig() {
        section("app config") {
            keyValuePair("has identity", identityProvider.hasIdentity())
            keyValuePair("locale", Locale.getDefault())
            keyValuePair(
                "uses passphrase",
                try {
                    masterKeyManager.isProtectedWithPassphrase()
                } catch (_: Exception) {
                    null
                },
            )
            keyValuePair("uses multi device", multiDeviceManager?.isMultiDeviceActive)
            keyValuePair("uses threema push", preferenceService.mustUseThreemaPush())
            keyValuePair("uses contact sync", preferenceService.isContactSyncEnabled())
        }
    }

    private fun StringBuilder.appendRestrictions(appRestrictionService: AppRestrictionService) {
        appRestrictionService.mdmSource.getStringRepresentation()?.let { mdmSourceString ->
            section("restrictions") {
                keyValuePair("mdm source", mdmSourceString)
                keyValuePair("username configured", appRestrictions.getLicenseUsername() != null)
                keyValuePair("password configured", appRestrictions.getLicensePassword() != null)
            }
        }
    }

    private fun StringBuilder.appendVersionHistory() {
        section("app version history") {
            appVersionHistoryManager.getHistory().forEach { record ->
                appendLine(
                    with(record) { "- $versionName ($versionCode) first opened on $time" },
                )
            }
        }
    }

    private fun StringBuilder.keyValuePair(key: String, value: Any?) {
        appendLine("$key:\t$value")
    }

    private fun StringBuilder.section(title: String, block: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("# $title")
        block()
    }
}
