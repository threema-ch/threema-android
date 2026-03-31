package config

import com.android.build.api.dsl.VariantDimension
import utils.intBuildConfigField
import utils.stringBuildConfigField

fun VariantDimension.setSentryConfig(
    projectId: Int,
    publicApikey: String,
    host: String = "bugs.threema.ch",
) {
    intBuildConfigField("SENTRY_PROJECT_ID", projectId)
    stringBuildConfigField("SENTRY_PUBLIC_API_KEY", publicApikey)
    stringBuildConfigField("SENTRY_HOST", host)
}

object SentryConfig {
    const val SANDBOX_PROJECT_ID = 33
    const val SANDBOX_PUBLIC_API_KEY = "b3e20afbf356a8748bb62ac165aa780c"
}
