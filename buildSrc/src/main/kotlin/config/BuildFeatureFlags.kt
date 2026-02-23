package config

import utils.LocalProperties

/**
 * Can be used to check whether certain features are explicitly enabled or disabled locally, i.e., during development on the current machine,
 * by checking whether the feature flag is set in the 'local.properties' file.
 *
 * To explicitly enable or disable a feature, add *threema.features.<name-of-feature> = [true|false]* to the 'local.properties' file
 * in the project root directory.
 */
object BuildFeatureFlags {
    operator fun get(feature: String): Boolean? =
        LocalProperties.getBoolean("threema.features.$feature")
}
