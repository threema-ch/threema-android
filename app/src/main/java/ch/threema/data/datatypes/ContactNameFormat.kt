package ch.threema.data.datatypes

import android.content.Context
import androidx.annotation.StringRes
import ch.threema.app.R

/**
 *  @param valueRes The string resource id of the string value that is saved in the preferences as a value for this setting.
 */
enum class ContactNameFormat(
    @JvmField @param:StringRes val valueRes: Int,
) {
    FIRSTNAME_LASTNAME(R.string.contact_format__first_name_last_name),
    LASTNAME_FIRSTNAME(R.string.contact_format__last_name_first_name),
    ;

    companion object {

        @JvmField
        val DEFAULT = FIRSTNAME_LASTNAME

        @JvmStatic
        fun fromValue(value: String, context: Context): ContactNameFormat? =
            entries.firstOrNull { contactNameFormat ->
                context.getString(contactNameFormat.valueRes) == value
            }
    }
}
