package ch.threema.app.errorreporting

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Http
import ch.threema.common.TimeProvider
import ch.threema.common.buildRequest
import ch.threema.common.executeAsync
import ch.threema.common.throwIfNotSuccessful
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

private val logger = getThreemaLogger("SentryService")

/**
 * Allows sending [ErrorRecord]s to the Sentry server, using its Envelope API.
 *
 * See https://develop.sentry.dev/sdk/overview/
 */
class SentryService(
    private val okHttpClient: OkHttpClient,
    private val timeProvider: TimeProvider,
    private val sentryIdProvider: SentryIdProvider,
    private val config: Config,
    private val metaInfo: MetaInfo,
) {
    @Throws(IOException::class)
    suspend fun sendErrorRecord(record: ErrorRecord) {
        if (config.publicApiKey.isEmpty()) {
            return
        }
        val request = buildRequest(buildRequestBody(record))
        val response = okHttpClient.executeAsync(request)
        if (!response.isSuccessful) {
            logger.error("Sentry responded with error: {}", response.body.string())
        }
        response.throwIfNotSuccessful()
    }

    private fun buildRequest(body: String) = buildRequest {
        url("https://${config.host}/api/${config.projectId}/envelope/")
        header("X-Sentry-Auth", "Sentry sentry_version=$SENTRY_VERSION, sentry_client=$CLIENT_NAME, sentry_key=${config.publicApiKey}")
        header(Http.Header.USER_AGENT, CLIENT_NAME)
        header(Http.Header.CONTENT_TYPE, CONTENT_TYPE)
        post(body.toRequestBody(CONTENT_TYPE.toMediaType()))
        gzip()
    }

    private fun buildRequestBody(record: ErrorRecord): String {
        val eventContent = buildEventItem(record)
        return buildString {
            appendLine(
                json.encodeToString(
                    SentAtHeader(
                        timeProvider.get().toString(),
                    ),
                ),
            )
            appendLine(
                json.encodeToString(
                    EventHeader(
                        type = "event",
                        length = eventContent.length,
                    ),
                ),
            )
            appendLine(eventContent)
        }
    }

    private fun buildEventItem(record: ErrorRecord): String {
        val event = Event(
            eventId = record.id.format(),
            timestamp = record.createdAt.toString(),
            release = metaInfo.appVersion,
            dist = metaInfo.versionCode.toString(),
            tags = Tags(
                androidVersion = metaInfo.androidSdkVersion.toString(),
                buildFlavor = metaInfo.buildFlavor,
            ),
            user = User(
                id = sentryIdProvider.getOrGenerateSentryId(),
            ),
            contexts = Contexts(
                device = Device(
                    model = metaInfo.deviceModel,
                ),
            ),
            exception = ExceptionItems(
                values = record.exceptions.map { exception ->
                    ExceptionItem(
                        type = exception.type,
                        module = exception.packageName,
                        value = exception.message,
                        stackTrace = buildStackTrace(exception),
                    )
                },
            ),
        )
        return json.encodeToString(event)
    }

    private fun buildStackTrace(exception: ErrorRecordExceptionDetails): StackTrace? {
        if (exception.stackTrace.isEmpty()) {
            return null
        }
        return StackTrace(
            frames = exception.stackTrace.map { element ->
                StackTraceFrame(
                    fileName = element.fileName,
                    module = element.className,
                    lineNumber = element.lineNumber.takeIf { it >= 0 },
                    rawFunction = element.methodName,
                    native = element.isNative,
                    inApp = element.isInApp,
                )
            },
        )
    }

    @Serializable
    private data class SentAtHeader(
        @SerialName("sent_at")
        val sentAt: String,
    )

    @Serializable
    private data class EventHeader(
        val type: String,
        val length: Int,
    )

    @Serializable
    private data class Event(
        @SerialName("event_id")
        val eventId: String,
        val timestamp: String,
        val platform: String = "android",
        val level: String = "fatal",
        val release: String,
        val dist: String,
        val tags: Tags,
        val user: User,
        val contexts: Contexts,
        val exception: ExceptionItems,
    )

    @Serializable
    private data class Tags(
        @SerialName("android_version")
        val androidVersion: String,
        @SerialName("build_flavor")
        val buildFlavor: String,
    )

    @Serializable
    private data class User(
        val id: String,
    )

    @Serializable
    private data class Contexts(
        val device: Device,
    )

    @Serializable
    private data class Device(
        val name: String = "Android",
        val model: String,
    )

    @Serializable
    private data class ExceptionItems(
        val values: List<ExceptionItem>,
    )

    @Serializable
    private data class ExceptionItem(
        val type: String,
        val value: String?,
        val module: String?,
        val mechanism: Mechanism = Mechanism(),
        @SerialName("stacktrace")
        val stackTrace: StackTrace?,
    )

    @Serializable
    private data class StackTrace(
        val frames: List<StackTraceFrame>,
    )

    @Serializable
    private data class StackTraceFrame(
        @SerialName("filename")
        val fileName: String?,
        val module: String?,
        @SerialName("lineno")
        val lineNumber: Int?,
        @SerialName("raw_function")
        val rawFunction: String?,
        val native: Boolean,
        @SerialName("in_app")
        val inApp: Boolean,
    )

    @Serializable
    private data class Mechanism(
        val type: String = "generic",
        val handled: Boolean = false,
    )

    data class Config(
        val host: String,
        val projectId: Int,
        val publicApiKey: String,
    )

    data class MetaInfo(
        val deviceModel: String,
        val androidSdkVersion: Int,
        val appVersion: String,
        val versionCode: Int,
        val buildFlavor: String,
    )

    companion object {
        private const val SENTRY_VERSION = 7
        private const val CLIENT_NAME = "threema.android/1.0"
        private const val CONTENT_TYPE = "application/x-sentry-envelope"

        private val json = Json {
            prettyPrint = false
            encodeDefaults = true
        }

        private fun UUID.format() =
            toString().replace("-", "").lowercase()
    }
}
