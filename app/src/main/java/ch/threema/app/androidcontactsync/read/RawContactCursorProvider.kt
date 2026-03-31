package ch.threema.app.androidcontactsync.read

import android.content.Context
import ch.threema.app.androidcontactsync.types.LookupInfo

class RawContactCursorProvider(
    private val appContext: Context,
) {
    /**
     * Create a raw contact cursor that reads all raw contacts.
     *
     * @throws [RawContactCursor.Companion.CursorCreateException] if the cursor could not be created
     * @throws [SecurityException] if there is no permission to read the contacts
     */
    @Throws(RawContactCursor.Companion.CursorCreateException::class, SecurityException::class)
    fun getRawContactCursor(): RawContactCursor =
        RawContactCursor.createRawContactCursor(
            contentResolver = appContext.contentResolver,
        )

    /**
     * Create a raw contact cursor that reads the contact with the given lookup information.
     *
     * @throws [RawContactCursor.Companion.CursorCreateException] if the cursor could not be created
     * @throws [SecurityException] if there is no permission to read the contacts
     */
    fun getRawContactCursorForLookup(lookupInfo: LookupInfo) =
        RawContactCursor.createRawContactCursorForLookup(
            contentResolver = appContext.contentResolver,
            lookupInfo = lookupInfo,
        )
}
