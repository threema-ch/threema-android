/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.mediaattacher

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.VCardExtractor
import ch.threema.base.utils.getThreemaLogger
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.property.FormattedName
import ezvcard.property.StructuredName
import ezvcard.property.VCardProperty
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("EditSendContactViewModel")

/**
 * Contains the data needed in the EditSendContactActivity.
 */
class EditSendContactViewModel : ViewModel() {
    /* The currently shown formatted name in the edit texts */
    private val formattedName: MutableLiveData<FormattedName> = MutableLiveData()

    /* The currently shown structured name in the edit texts */
    private val structuredName: MutableLiveData<StructuredName> = MutableLiveData()

    /* The properties (except the name properties) */
    private val properties: MutableLiveData<MutableMap<VCardProperty, Boolean>> = MutableLiveData()

    /* The modified contact that should be sent */
    private val modifiedContact: MutableLiveData<Pair<String, File>> = MutableLiveData()

    /* The state of the bottom sheet */
    var bottomSheetExpanded: Boolean = false

    /**
     * Get formatted name live data
     */
    fun getFormattedName(): LiveData<FormattedName> = formattedName

    /**
     * Get structured name live data
     */
    fun getStructuredName(): LiveData<StructuredName> = structuredName

    /**
     * Get property live data
     */
    fun getProperties(): LiveData<MutableMap<VCardProperty, Boolean>> = properties

    /**
     * Get the modified contact (ready to be sent)
     * @return a pair with the name of the contact and a vCard file
     */
    fun getModifiedContact(): LiveData<Pair<String, File>> = modifiedContact

    /**
     * Initializes the view model based on the given contact uri.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    fun initializeContact(
        contactUri: Uri,
        contentResolver: ContentResolver,
        extractor: VCardExtractor,
    ) {
        if (properties.value != null) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val vCard = contentResolver.openInputStream(contactUri).use {
                BufferedReader(InputStreamReader(it)).useLines { l -> l.joinToString("\n") }
            }.let {
                Ezvcard.parse(it).first()
            }

            if (createFormattedName(
                    vCard,
                    extractor,
                ) == "" && vCard.formattedName?.value ?: "" != ""
            ) {
                formattedName.postValue(vCard.formattedName)
            } else {
                structuredName.postValue(vCard.structuredName ?: StructuredName())
            }

            properties.postValue(vCard.properties.associateWith { true }.toMutableMap())
        }
    }

    /**
     * Get the formatted name and the vCard as file containing the selected properties.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    fun prepareFinalVCard(context: Context, cacheDir: File, contactUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val vCard = VCard()
            if (structuredName.value != null) {
                vCard.setProperty(structuredName.value)
                vCard.setProperty(
                    FormattedName(
                        createFormattedName(
                            vCard,
                            VCardExtractor(DateFormat.getDateFormat(context), context.resources),
                        ),
                    ),
                )
            } else if (formattedName.value != null) {
                vCard.setProperty(formattedName.value)
            }

            // Add selected properties to the vcard
            properties.value?.filter { it.value }?.map { it.key }?.forEach {
                vCard.addProperty(it)
            }

            val mimeType = FileUtil.getMimeTypeFromUri(context, contactUri)
            val modifiedContactFile = File(cacheDir, FileUtil.getDefaultFilename(mimeType))

            val writer = Ezvcard.write(vCard).prodId(false)
            writer.go(modifiedContactFile)

            modifiedContact.postValue((vCard.formattedName?.value ?: "") to modifiedContactFile)
        }
    }

    /**
     * Create the formatted name (FN) based on the structured name (N).
     */
    private fun createFormattedName(vcard: VCard, extractor: VCardExtractor): String {
        if (vcard.structuredName != null) {
            try {
                return extractor.getText(vcard.structuredName, false).trim()
            } catch (e: Exception) {
                if (e !is VCardExtractor.VCardExtractionException) {
                    logger.error("Could not extract name of contact", e)
                }
            }
        }
        return ""
    }
}
