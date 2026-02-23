package config

import com.android.build.api.dsl.VariantDimension
import utils.stringResValue

/**
 * Configure the names used for the product flavor.
 *
 * @param appName The full name of the app, e.g. "Threema Libre". Used in places where we refer to the app itself,
 * as opposed to a feature, the Threema service or the company.
 * @param shortAppName The short version of the app name, e.g. "Threema". This will also be used in terms like "Threema ID", "Threema Safe", etc.
 * @param companyName The name of the company that operates the app's servers and/or distributes the app.
 * @param appNameDesktop The name of the corresponding desktop client (multi-device)
 */
fun VariantDimension.setProductNames(
    appName: String,
    shortAppName: String = if ("Threema" in appName) "Threema" else appName,
    companyName: String = "Threema",
    appNameDesktop: String = appName,
) {
    stringResValue("app_name", appName.nonBreaking())
    stringResValue("app_name_short", shortAppName.nonBreaking())
    stringResValue("company_name", companyName.nonBreaking())
    stringResValue("app_name_desktop", appNameDesktop.nonBreaking())
}

private fun String.nonBreaking() =
    replace(" ", "\u00A0")
