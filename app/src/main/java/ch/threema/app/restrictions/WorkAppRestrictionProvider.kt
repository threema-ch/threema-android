package ch.threema.app.restrictions

import android.os.Bundle
import ch.threema.common.takeUnlessEmpty

class WorkAppRestrictionProvider(
    private val getRestrictions: () -> Bundle?,
) : AppRestrictionProvider {
    override fun getBooleanRestriction(restriction: String): Boolean? =
        getRestrictions()
            ?.takeIf { it.containsKey(restriction) }
            ?.getBoolean(restriction)

    override fun getStringRestriction(restriction: String): String? =
        getRestrictions()
            ?.takeIf { it.containsKey(restriction) }
            ?.getString(restriction)
            ?.takeUnlessEmpty()

    override fun getIntRestriction(restriction: String): Int? =
        getRestrictions()
            ?.takeIf { it.containsKey(restriction) }
            ?.getInt(restriction)
}
