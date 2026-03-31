package ch.threema.app.androidcontactsync.read

import android.net.Uri
import ch.threema.app.androidcontactsync.read.RawContactCursor.Companion.CursorCreateException
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("RawContactReader")

class RawContactReader(
    private val rawContactCursorProvider: RawContactCursorProvider,
    private val lookupInfoReader: LookupInfoReader,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val contactReadMutex = Mutex()

    /**
     * Reads all raw contacts from android's contact provider.
     *
     * @throws AndroidContactReadException if reading the raw contacts fails
     */
    @Throws(AndroidContactReadException::class)
    suspend fun readAllRawContacts(): Set<RawContact> = withMutuallyExclusiveIoContext {
        try {
            readRawContactsFromCursor(rawContactCursorProvider.getRawContactCursor())
        } catch (e: CursorCreateException) {
            throw AndroidContactReadException.Other(cause = e)
        } catch (e: SecurityException) {
            throw AndroidContactReadException.MissingPermission(cause = e)
        }
    }

    /**
     * Reads all raw contacts from android's contact provider using the [androidContactLookupUri].
     *
     * @throws AndroidContactReadException if reading the raw contacts fails
     * @throws AndroidContactLookupException if the contact lookup fails
     */
    @Throws(AndroidContactReadException::class, AndroidContactLookupException::class)
    suspend fun readRawContactsFromLookup(androidContactLookupUri: Uri): Set<RawContact> = withMutuallyExclusiveIoContext {
        val lookupInfo = getCurrentLookupInfo(androidContactLookupUri) ?: run {
            logger.info("Could not find current lookup info")
            return@withMutuallyExclusiveIoContext emptySet()
        }

        val rawContacts = try {
            readRawContactsFromCursor(rawContactCursorProvider.getRawContactCursorForLookup(lookupInfo))
        } catch (e: CursorCreateException) {
            throw AndroidContactReadException.Other(cause = e)
        } catch (e: SecurityException) {
            throw AndroidContactReadException.MissingPermission(cause = e)
        }

        val verificationLookupInfo = getCurrentLookupInfo(androidContactLookupUri) ?: run {
            logger.error("Could not find the lookup information in verification lookup after reading the raw contact")
            throw AndroidContactLookupException.UnstableContactLookupInfo(message = "No lookup information found in verification lookup")
        }

        // We check that the lookup info is still the same after reading the contact data from the contact provider. The lookup key may change if the
        // contact gets modified and the contact id can change when a sync has happened or the data is corrupt.
        if (lookupInfo != verificationLookupInfo) {
            throw AndroidContactLookupException.UnstableContactLookupInfo(message = "Lookup info has change while reading raw contacts")
        }

        // Check that all raw contacts that have been returned have the same (and correct) lookup information.
        if (rawContacts.any { rawContact -> rawContact.lookupInfo != verificationLookupInfo }) {
            throw AndroidContactLookupException.WrongContactReceivedForLookup()
        }

        return@withMutuallyExclusiveIoContext rawContacts
    }

    @Throws(AndroidContactReadException::class)
    private fun getCurrentLookupInfo(androidContactLookupUri: Uri): LookupInfo? = try {
        lookupInfoReader.getLookupInfo(androidContactLookupUri)
    } catch (e: LookupInfoException) {
        if (e is LookupInfoException.MissingPermission) {
            throw AndroidContactReadException.MissingPermission()
        }
        throw AndroidContactLookupException.LookupInfoException(cause = e)
    }

    @Throws(AndroidContactReadException::class)
    private fun readRawContactsFromCursor(rawContactCursor: RawContactCursor): Set<RawContact> {
        val rawContacts = try {
            rawContactCursor.use { cursor ->
                val rawContactBuilders = mutableMapOf<RawContactId, RawContact.Builder>()

                cursor
                    .mapNotNull(RawContactCursor::getContactDataRowOrNull)
                    .forEach { contactDataRow ->
                        val rawContactBuilder = rawContactBuilders.getOrPut(
                            key = contactDataRow.rawContactId,
                            defaultValue = {
                                RawContact.Builder(
                                    rawContactId = contactDataRow.rawContactId,
                                    lookupInfo = contactDataRow.lookupInfo,
                                )
                            },
                        )
                        rawContactBuilder.addContactDataRow(contactDataRow)
                    }

                rawContactBuilders
                    .values
                    .map(RawContact.Builder::build)
                    .toSet()
            }
        } catch (e: RawContact.Builder.RawContactBuildException) {
            throw AndroidContactReadException.MultipleLookupKeysPerRawContact(e)
        } catch (e: CursorCreateException) {
            throw AndroidContactReadException.Other(cause = e)
        } catch (e: SecurityException) {
            throw AndroidContactReadException.MissingPermission(cause = e)
        } catch (e: Exception) {
            throw AndroidContactReadException.Other(cause = e)
        }

        checkLookupKeyToContactIdRelationOrThrow(rawContacts)

        return rawContacts
    }

    @Throws(AndroidContactReadException::class)
    private fun checkLookupKeyToContactIdRelationOrThrow(rawContacts: Set<RawContact>) {
        // Check that each lookup key is only used with a unique contact id. If there is a lookup key that is used together with different contact ids,
        // then we abort reading the contacts.
        val lookupKeyToContactId = rawContacts
            .map(RawContact::lookupInfo)
            .groupBy(LookupInfo::lookupKey)
            .mapValues { (_, value) ->
                // Get all contact ids that are found per lookup key. Normally this should just be one id. Throw an exception if there are several
                // contact ids per lookup key. Note that we know at this point that there is at least one element because of the usage of the groupBy
                // extension function.
                val contactIds = value.map(LookupInfo::contactId).toSet()
                contactIds.singleOrNull() ?: throw AndroidContactReadException.MultipleContactIdsPerLookupKey()
            }

        if (lookupKeyToContactId.size > lookupKeyToContactId.values.toSet().size) {
            throw AndroidContactReadException.MultipleLookupKeysPerContactId()
        }
    }

    private suspend inline fun <T> withMutuallyExclusiveIoContext(crossinline block: suspend () -> T): T = contactReadMutex.withLock {
        withContext(dispatcherProvider.io) {
            block()
        }
    }
}

private fun RawContactCursor.getContactDataRowOrNull(): ContactDataRow? =
    try {
        getContactDataRow()
    } catch (cursorException: RawContactCursor.Companion.CursorException) {
        when (cursorException) {
            is RawContactCursor.Companion.CursorException.IllegalMimeType -> logger.warn(
                "Ignoring row with illegal mime type '{}'",
                cursorException.mimeType,
            )

            is RawContactCursor.Companion.CursorException.MissingProperty -> logger.warn(
                "Row is missing property '{}'",
                cursorException.propertyName,
            )
        }
        null
    }

private fun <R> RawContactCursor.mapNotNull(transform: (RawContactCursor) -> R?) =
    generateSequence { if (this.moveToNext()) this else null }
        .mapNotNull(transform)
