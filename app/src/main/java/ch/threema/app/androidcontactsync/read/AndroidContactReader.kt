package ch.threema.app.androidcontactsync.read

import android.net.Uri
import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.RawContact
import kotlin.collections.component1

class AndroidContactReader(
    private val rawContactReader: RawContactReader,
) {
    /**
     * Reads all contacts from android's contact provider.
     *
     * @throws AndroidContactReadException if reading the contacts fails
     */
    @Throws(AndroidContactReadException::class)
    suspend fun readAllAndroidContacts(): Set<AndroidContact> =
        rawContactReader
            .readAllRawContacts()
            .groupToAndroidContacts()

    /**
     * Reads the specific contact from android's contact provider. Note that the lookup information from the returned contact may differ from
     * [androidContactLookupUri] as the contact may have been modified in the meantime.
     *
     * @return null if there was no contact with the lookup information found
     *
     * @throws AndroidContactReadException if reading the contacts fails
     * @throws AndroidContactLookupException if multiple contacts are returned by the lookup
     */
    @Throws(AndroidContactReadException::class, AndroidContactLookupException::class)
    suspend fun readAndroidContactWithLookup(androidContactLookupUri: Uri): AndroidContact? {
        val androidContacts = rawContactReader
            .readRawContactsFromLookup(androidContactLookupUri)
            .groupToAndroidContacts()

        return when (val amountOfContacts = androidContacts.size) {
            0 -> null
            1 -> androidContacts.first()
            else -> throw AndroidContactLookupException.MultipleContactsReceivedForLookup(amountOfContacts)
        }
    }

    private fun Set<RawContact>.groupToAndroidContacts() =
        groupBy(RawContact::lookupInfo)
            .map { (lookupInfo, rawContacts) ->
                AndroidContact(
                    lookupInfo = lookupInfo,
                    rawContacts = rawContacts.toSet(),
                )
            }
            .toSet()
}
