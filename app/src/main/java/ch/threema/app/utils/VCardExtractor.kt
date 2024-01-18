/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.utils

import android.content.res.Resources
import androidx.annotation.DrawableRes
import ch.threema.app.R
import ezvcard.parameter.AddressType
import ezvcard.parameter.EmailType
import ezvcard.parameter.RelatedType
import ezvcard.parameter.TelephoneType
import ezvcard.property.*
import java.util.*

/**
 * This class provides the functionality to get the text of a VCard property.
 */
class VCardExtractor(private val dateFormat: java.text.DateFormat, private val resources: Resources) {

    /**
     * Extracts the text of the given VCard property.
     * @param property the vcard property
     * @param ignoreName ignores StructuredName properties (i.e., it throws an exception for those)
     * @throws VCardExtractionException if the property is invalid and should not be included
     */
    fun getText(property: VCardProperty, ignoreName: Boolean = true): String = when (property) {
        is Address -> getAddress(property)
        is Agent -> getAgent(property)
        is BinaryProperty<*> -> getBinaryProperty(property)
        is ClientPidMap -> "PID Map"
        is DateOrTimeProperty -> getDateOrTimeProperty(property)
        is Gender -> property.gender?.toNonEmpty() ?: unknownProperty()
        is Geo -> property.geoUri.toString().toNonEmpty()
        is Impp -> property.handle?.toNullIfEmpty() ?: "IMPP"
        is ListProperty<*> -> getListProperty(property)
        is PlaceProperty -> getPlaceProperty(property)
        is Related -> getRelated(property)
        is SimpleProperty<*> -> getSimpleProperty(property)
        is StructuredName -> if (ignoreName) throw VCardExtractionException("name should be ignored") else getStructuredName(property)
        is Telephone -> getTelephone(property)
        is Timezone -> listOfNotNull(property.text, property.offset.toString()).joinToString(", ").toNonEmpty()
        is Xml -> "XML"
        else -> unknownProperty()
    }

    /**
     * Extracts the descriptions of the given VCard property
     */
    fun getDescription(property: VCardProperty): String = when (property) {
        is Address -> getAddressTypes(property)
        is DateOrTimeProperty -> getDateOrTimePropertyTypes(property)
        is Impp -> property.protocol ?: ""
        is ListProperty<*> -> getListPropertyType(property)
        is Related -> getRelatedType(property)
        is SimpleProperty<*> -> getSimplePropertyType(property)
        is Telephone -> getTelephoneType(property)
        else -> ""
    }

    /**
     * Returns the resource id of the suitable icon for the given VCard property
     */
    @DrawableRes
    fun getIcon(property: VCardProperty): Pair<Int, Int> {
        return when (property) {
            is Address -> R.drawable.ic_contact_property_location to R.drawable.ic_contact_property_location_gray
            is DateOrTimeProperty -> R.drawable.ic_contact_property_date to R.drawable.ic_contact_property_date_gray
            is PlaceProperty -> R.drawable.ic_contact_property_location to R.drawable.ic_contact_property_location_gray
            is Telephone -> R.drawable.ic_contact_property_phone to R.drawable.ic_contact_property_phone_gray
            is Email -> R.drawable.ic_contact_property_email to R.drawable.ic_contact_property_email_gray
            else -> R.drawable.ic_contact_property_info to R.drawable.ic_contact_property_info_gray
        }
    }

    private fun getAddress(property: Address): String =
            StringBuilder().apply {
                if (property.streetAddress != null) append(property.streetAddress)
                if (property.extendedAddress != null) append(property.extendedAddress)
                if (property.postalCode != null) append("\n${property.postalCode} ")
                if (property.postalCode == null && property.locality != null) append("\n ")
                if (property.locality != null) append(property.locality)
                if (property.region != null) append("\n${property.region}")
                if (property.country != null) append("\n${property.country}")
            }.toString().toNonEmpty()

    private fun getAgent(property: Agent): String {
        val sb = StringBuilder()
        if (property.vCard != null) {
            property.vCard.structuredName?.let { sb.append(getText(it, false)) }
            sb.append(property.vCard.properties.joinToString("\n", transform = this::getText))
        }
        if (property.url != null && property.url != "") {
            sb.append("\n")
            sb.append(property.url)
        }
        return sb.toString().toNonEmpty()
    }

    private fun getBinaryProperty(property: BinaryProperty<*>): String = when (property) {
        is ImageProperty -> property.url
                ?: "" // Include image urls if available, otherwise show picture instead of url
        is Key -> {
            when {
                property.url != null && property.url.trim().isNotEmpty() -> property.url.trim()
                else -> resources.getString(R.string.contact_property_key)
            }
        }
        is Sound -> {
            when {
                property.url != null && property.url.trim().isNotEmpty() -> property.url
                else -> "" // Don't include Audios without an URL
            }
        }
        else -> unknownProperty()
    }

    private fun getDateOrTimeProperty(property: DateOrTimeProperty): String =
            if (property.text != null && property.text != "") {
                property.text
            } else {
                dateFormat.format((when {
                    property.calendar != null -> property.calendar
                    property.partialDate != null -> {
                        val pd = property.partialDate
                        Calendar.getInstance().apply {
                            clear()
                            pd.date?.let { set(Calendar.DAY_OF_MONTH, it) }
                            pd.month?.let { set(Calendar.MONTH, it - 1) }
                            pd.year?.let { set(Calendar.YEAR, it) }
                        }
                    }
                    else -> unknownProperty()
                }).time)
            }

    private fun getListProperty(property: ListProperty<*>): String =
            when (property) {
                is TextListProperty -> if (property.values != null) {
                    property.values.joinToString(", ").toNonEmpty()
                } else {
                    unknownProperty()
                }
                else -> unknownProperty()
            }

    private fun getPlaceProperty(property: PlaceProperty) = property.text?.toNullIfEmpty()
            ?: property.uri?.toNullIfEmpty()
            ?: property.geoUri?.toString()?.toNullIfEmpty() ?: unknownProperty()

    private fun getRelated(property: Related) = (property.text ?: property.uri ?: "").let {
        if (it.startsWith("mailto:")) it.drop(7) else it
    }.toNonEmpty()

    private fun getSimpleProperty(property: SimpleProperty<*>): String =
            when (property) {
                is Revision -> unknownProperty() // don't show revisions
                is RawProperty -> property.value?.let { v ->
                    if (v.startsWith("vnd.android.cursor.item/")) {
                        v.split(";").let { if (it.size >= 2) it[1] else "" }.toNonEmpty()
                    } else v.toNonEmpty()
                } ?: unknownProperty()
                is TextProperty -> property.value.toNonEmpty()
                else -> unknownProperty()
            }

    private fun getStructuredName(property: StructuredName): String =
            StringBuilder().apply {
                append(listOf(
                        property.prefixes,
                        listOf(property.given),
                        property.additionalNames,
                        listOf(property.family)
                ).flatten().filterNotNull().filter { it.isNotBlank() }.joinToString(" ") { it.trim() })
                if (property.suffixes.any { it.isNotBlank() }) {
                    append(property.suffixes.joinToString(" ", prefix = ", ").trim())
                }
            }.toString().toNonEmpty()

    private fun getTelephone(property: Telephone) =
            property.text?.toNullIfEmpty()
                    ?: property.uri?.toString()?.toNullIfEmpty() ?: unknownProperty()

    private fun getAddressTypes(property: Address) = property.types.map {
        when (it) {
            AddressType.HOME -> resources.getString(R.string.postalTypeHome)
            AddressType.WORK -> resources.getString(R.string.postalTypeWork)
            else -> resources.getString(R.string.postalTypeOther)
        }
    }.distinct().joinToString(", ").let { if (it != "") it else resources.getString(R.string.postalTypeOther) }

    private fun getDateOrTimePropertyTypes(property: DateOrTimeProperty) = when (property) {
        is Anniversary -> resources.getString(R.string.eventTypeAnniversary)
        is Birthday -> resources.getString(R.string.eventTypeBirthday)
        else -> resources.getString(R.string.eventTypeOther)
    }

    private fun getListPropertyType(property: ListProperty<*>) = when (property) {
        is Nickname -> resources.getString(R.string.header_nickname_entry)
        is Organization -> resources.getString(R.string.organization_type)
        else -> ""
    }

    private fun getRelatedType(property: Related) = property.types.joinToString(", ") {
        when (it) {
            RelatedType.CHILD -> resources.getString(R.string.relationTypeChild)
            RelatedType.FRIEND -> resources.getString(R.string.relationTypeFriend)
            RelatedType.PARENT -> resources.getString(R.string.relationTypeParent)
            RelatedType.SPOUSE -> resources.getString(R.string.relationTypeSpouse)
            else -> resources.getString(R.string.relationTypeCustom)
        }
    }

    private fun getSimplePropertyType(property: SimpleProperty<*>) = when (property) {
        is TextProperty -> {
            when (property) {
                is Email -> {
                    property.types.joinToString(", ") {
                        when (it) {
                            EmailType.HOME -> resources.getString(R.string.emailTypeHome)
                            EmailType.WORK -> resources.getString(R.string.emailTypeWork)
                            else -> resources.getString(R.string.emailTypeOther)
                        }
                    }.let {
                        if (it != "") it else resources.getString(R.string.emailTypeOther)
                    }
                }
                is RawProperty -> property.propertyName.let {
                    when {
                        property.value.startsWith("vnd.android.cursor.item/nickname") -> resources.getString(R.string.header_nickname_entry)
                        it == "X-ANDROID-CUSTOM" -> ""
                        it.startsWith("X-", true) -> it.drop(2)
                        else -> it
                    }
                }
                else -> ""
            }
        }
        else -> ""
    }

    private fun getTelephoneType(property: Telephone) = property.types.joinToString(", ") {
        when (it) {
            TelephoneType.HOME -> resources.getString(R.string.phoneTypeHome)
            TelephoneType.CELL -> resources.getString(R.string.phoneTypeMobile)
            TelephoneType.WORK -> resources.getString(R.string.phoneTypeWork)
            TelephoneType.PAGER -> resources.getString(R.string.phoneTypePager)
            TelephoneType.CAR -> resources.getString(R.string.phoneTypeCar)
            TelephoneType.ISDN -> resources.getString(R.string.phoneTypeIsdn)
            TelephoneType.FAX -> resources.getString(R.string.phoneTypeOtherFax)
            else -> resources.getString(R.string.phoneTypeOther)
        }
    }

    private fun String.toNullIfEmpty() = if (this.trim().isEmpty()) null else this

    private fun String.toNonEmpty() = this.trim().also {
        if (it.isEmpty()) {
            throw VCardExtractionException("Invalid property (must be non empty)")
        }
    }

    private fun unknownProperty(): Nothing = throw VCardExtractionException("unknown property")

    class VCardExtractionException(msg: String) : Exception(msg)

}
