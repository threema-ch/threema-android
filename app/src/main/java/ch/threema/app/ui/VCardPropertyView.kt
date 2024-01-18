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

package ch.threema.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.VCardExtractor
import ch.threema.base.utils.LoggingUtil
import ezvcard.property.FormattedName
import ezvcard.property.ImageProperty
import ezvcard.property.StructuredName
import ezvcard.property.VCardProperty
import java.io.ByteArrayInputStream

private val logger = LoggingUtil.getThreemaLogger("VCardPropertyView")

/**
 * This class manages the presentation and the selection of a VCard property.
 */
class VCardPropertyView(context: Context) : FrameLayout(context) {

    private val container: View
    private val checkbox: CheckBox
    private val contactPropertyText: TextView
    private val contactPropertyType: TextView
    private val contactPropertyIcon: ImageView
    private val contactPropertyPhoto: ImageView

    @DrawableRes
    private var iconIncluded: Int? = null

    @DrawableRes
    private var iconExcluded: Int? = null
    private var drawableIncluded: Drawable? = null
    private var drawableExcluded: Drawable? = null

    private var onChange: (isChecked: Boolean) -> Unit = { }

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.item_edit_send_contact_property, this)
        container = findViewById(R.id.contact_edit_item)
        checkbox = findViewById(R.id.include_checkbox)
        contactPropertyText = findViewById(R.id.contact_property_text)
        contactPropertyType = findViewById(R.id.contact_property_type)
        contactPropertyIcon = findViewById(R.id.contact_property_icon)
        contactPropertyPhoto = findViewById(R.id.contact_property_photo)
    }

    /**
     * Initialize the presentation of the given property. After initialization, the property is
     * added or removed of the given VCard based on whether this property is checked or not.
     */
    fun initializeProperty(property: VCardProperty, include: Boolean): Boolean {
        if (property is FormattedName || property is StructuredName) {
            return false
        }

        val extractor = VCardExtractor(DateFormat.getDateFormat(context), resources)

        try {
            contactPropertyText.text = extractor.getText(property)
            val descriptionText = extractor.getDescription(property)
            if (descriptionText != "") {
                contactPropertyType.text = descriptionText
            } else {
                contactPropertyType.visibility = GONE
            }
        } catch (e: Exception) {
            if (e is VCardExtractor.VCardExtractionException) {
                logger.info("Invalid property will not be included: '$property'", e)
            } else {
                logger.error("Could not extract property '$property'", e)
            }
            return false
        }

        extractor.getIcon(property).let {
            iconIncluded = it.first
            iconExcluded = it.second
        }

        if (property is ImageProperty && property.data != null) {
            try {
                val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(property.data))
                drawableIncluded = RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
                    isCircular = true
                }
                drawableExcluded = RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
                    isCircular = true
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                }
                contactPropertyPhoto.apply {
                    visibility = View.VISIBLE
                    setImageDrawable(drawableIncluded)
                }
            } catch (e: Exception) {
                logger.warn("Couldn't display image as property; will not be included", e)
                return false
            }
        }

        checkbox.isChecked = include

        container.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
            onChange(checkbox.isChecked)
            updateLayout()
        }

        updateLayout()

        return true
    }

    fun isChecked() = checkbox.isChecked

    fun onChange(onChange: (isChecked: Boolean) -> Unit) {
        this.onChange = onChange
    }

    private fun updateLayout() {
        if (checkbox.isChecked) {
            iconIncluded?.let { contactPropertyIcon.setBackgroundResource(it) }
            contactPropertyPhoto.setImageDrawable(drawableIncluded)
            contactPropertyText.setTextColor(ConfigUtils.getColorFromAttribute(context, R.attr.colorOnBackground))
            contactPropertyType.setTextColor(ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurface))
        } else {
            iconExcluded?.let { contactPropertyIcon.setBackgroundResource(it) }
            contactPropertyPhoto.setImageDrawable(drawableExcluded)
            contactPropertyText.setTextColor(ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurfaceVariant))
            contactPropertyType.setTextColor(ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurfaceVariant))
        }
    }
}
