/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import ch.threema.app.R
import ch.threema.app.mediaattacher.ContactEditViewModel
import ch.threema.app.ui.VCardPropertyView
import ch.threema.app.utils.VCardExtractor
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.shape.MaterialShapeDrawable
import ezvcard.property.StructuredName

private val logger = LoggingUtil.getThreemaLogger("EditSendContactActivity")

/**
 * This activity lets the user select which properties of contact should be included before sending
 * it in a chat. The name of the contact can be modified.
 */
class EditSendContactActivity : ThreemaToolbarActivity() {

    private lateinit var viewModel: ContactEditViewModel
    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // set status bar color
        window.statusBarColor = resources.getColor(R.color.attach_status_bar_color_collapsed)

        toolbar = findViewById(R.id.toolbar_contact)
        appBarLayout = findViewById(R.id.appbar_layout_contact)
        viewModel = ViewModelProvider(this)[ContactEditViewModel::class.java]

        // Finish activity when chat activity (in "background") is clicked
        ((findViewById<CoordinatorLayout>(R.id.edit_send_contact_coordinator).parent as ViewGroup)
                .parent as ViewGroup).setOnClickListener { cancelAndFinish() }

        // Finish activity when bottom sheet gets hidden and adapt status bar color on expand/drag
        bottomSheet = findViewById<View>(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            addBottomSheetCallback(object : BottomSheetCallback() {

                override fun onStateChanged(view: View, i: Int) {
                    when (i) {
                        BottomSheetBehavior.STATE_HIDDEN -> cancelAndFinish()
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            onBottomSheetExpand()
                            viewModel.bottomSheetExpanded = true
                        }
                        BottomSheetBehavior.STATE_SETTLING -> {}
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {}
                        else -> {
                            onBottomSheetCollapse()
                            viewModel.bottomSheetExpanded = false
                        }
                    }
                }

                override fun onSlide(view: View, v: Float) {}
            })
        }

        toolbar.setNavigationOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            onBottomSheetCollapse()
        }

        // Set correct top margin depending on the toolbar height
        val rootCoordinator = findViewById<CoordinatorLayout>(R.id.edit_send_contact_coordinator)
        rootCoordinator.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootCoordinator.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val topMargin = toolbar.height - resources.getDimensionPixelSize(R.dimen.drag_handle_height)

                val bottomSheetContainer = findViewById<CoordinatorLayout>(R.id.bottom_sheet_coordinator)
                val bottomSheetContainerLayoutParams = bottomSheetContainer.layoutParams as CoordinatorLayout.LayoutParams
                bottomSheetContainerLayoutParams.setMargins(0, topMargin, 0, 0)
                bottomSheetContainer.layoutParams = bottomSheetContainerLayoutParams

                if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
                    bottomSheetBehavior.peekHeight = ((bottomSheetContainer.height / 16f) * 9f).toInt()
                } else if (resources.configuration.orientation == ORIENTATION_PORTRAIT) {
                    bottomSheetBehavior.peekHeight = -1
                }

                if (viewModel.bottomSheetExpanded) {
                    onBottomSheetExpand()
                } else {
                    onBottomSheetCollapse()
                }
            }
        })

        // Set the correct toolbar width
        val toolbarLayoutParams = toolbar.layoutParams
        toolbarLayoutParams.width = bottomSheetBehavior.maxWidth
        toolbar.layoutParams = toolbarLayoutParams

        if (viewModel.bottomSheetExpanded) {
            onBottomSheetExpand()
        }

        val contactUri = intent.extras?.get(EXTRA_CONTACT)
        if (contactUri == null || contactUri !is Uri) {
            logger.error("invalid contact uri: '$contactUri'")

            cancelAndFinish()
            return
        }

        viewModel.initializeContact(contactUri, contentResolver, VCardExtractor(DateFormat.getDateFormat(applicationContext), resources))

        // Show edit-texts for the name properties that are set in the contact
        val editTexts = listOf(
                NamePrefixWrapper(R.id.name_prefix_edit_text),
                FirstNameWrapper(R.id.first_name_edit_text),
                MiddleNameWrapper(R.id.middle_name_edit_text),
                LastNameWrapper(R.id.last_name_edit_text),
                NameSuffixWrapper(R.id.name_suffix_edit_text),
                FullNameWrapper(R.id.name_full_edit_text)
        )

        // Expand bottom sheet when the focused edit text is hidden behind the soft keyboard
        bottomSheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            Handler(Looper.getMainLooper())
                    .postDelayed({ editTexts.forEach { it.checkVisibility() } }, 20)
        }

        viewModel.getProperties().observe(this) { properties ->
            // Add (valid) properties to layout and keep track of include/exclude
            val propertyParentLayout = findViewById<LinearLayout>(R.id.property_container)
            val props = properties.keys.toList()
            props.forEach { p ->
                VCardPropertyView(this@EditSendContactActivity).let {
                    if (it.initializeProperty(p, properties[p] == true)) {
                        propertyParentLayout.addView(it)
                        it.onChange { checked -> properties[p] = checked }
                    } else {
                        properties[p] = false
                    }
                }
            }

            // Hide progress bar
            findViewById<CircularProgressIndicator>(R.id.progress_bar_parsing).visibility = View.GONE

            // Send the possibly modified VCard as file
            findViewById<FloatingActionButton>(R.id.send_contact).apply {
                setOnClickListener {
                    viewModel.prepareFinalVCard(context, cacheDir, contactUri)
                }
                visibility = View.VISIBLE
            }
        }

        viewModel.getModifiedContact().observe(this) {
            val (name, modifiedContact) = it

            val data = Intent().apply {
                putExtra(RESULT_CONTACT_URI, Uri.fromFile(modifiedContact))
                putExtra(RESULT_CONTACT_NAME, name)
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }

    override fun getLayoutResource() = R.layout.activity_edit_send_contact

    /**
     * Shows the toolbar and adapts the status bar color.
     */
    private fun onBottomSheetExpand() {
        appBarLayout.animation?.cancel()
        appBarLayout.alpha = 0f
        appBarLayout.visibility = View.VISIBLE
        appBarLayout.animate().alpha(1f).setDuration(100).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                appBarLayout.visibility = View.VISIBLE
            }
        })
        appBarLayout.postDelayed({
            val background: Drawable = bottomSheet.getBackground()
            if (background is MaterialShapeDrawable) {
                window.statusBarColor = background.resolvedTintColor
            } else {
                window.statusBarColor = resources.getColor(R.color.attach_status_bar_color_expanded)
            }
        }, 100)
    }

    /**
     * Hides the toolbar and adapts the status bar color.
     */
    private fun onBottomSheetCollapse() {
        appBarLayout.animation?.cancel()
        appBarLayout.alpha = 1f
        appBarLayout.animate().alpha(0f).setDuration(100).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                appBarLayout.visibility = View.INVISIBLE
                window.statusBarColor = resources.getColor(R.color.attach_status_bar_color_collapsed)
            }

            override fun onAnimationCancel(animation: Animator) {
                window.statusBarColor = resources.getColor(R.color.attach_status_bar_color_collapsed)
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })
    }

    /**
     * Finish the activity with canceled as result
     */
    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }

    /**
     * This class keeps track of the changes of a name field. If the name field is empty, no edit
     * text might be shown at all (depending on the name field type). The edit text is shown, if
     * the initial text argument is not null.
     */
    abstract inner class EditTextWrapper(@IdRes private val id: Int) {
        private val editText: EditText
        private val bottomSheetBehavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(this@EditSendContactActivity.findViewById(R.id.bottom_sheet))
        private val scrollView = this@EditSendContactActivity.findViewById<NestedScrollView>(R.id.nested_scroll_view)

        init {
            editText = this@EditSendContactActivity.findViewById<EditText>(id).apply {
                addTextChangedListener {
                    onTextChanged(it.toString())
                }

                setOnFocusChangeListener { _, hasFocus -> if (hasFocus) checkVisibility() }
            }
        }

        /**
         * If the edit text is focused and hidden (most likely behind the soft keyboard), the
         * bottom sheet is expanded.
         */
        fun checkVisibility() {
            if (editText.hasFocus() && !isFullyVisible()) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        protected fun setInitialText(initialText: String) {
            editText.setText(initialText)

            (editText.parent.parent as View).visibility = View.VISIBLE
        }

        /**
         * This method is called whenever the text of the edit text has been changed.
         */
        protected abstract fun onTextChanged(text: String)

        /**
         * Returns true, if the edit text is fully visible (inside the scroll view).
         */
        private fun isFullyVisible(): Boolean {
            val scrollBounds = Rect()
            scrollView.getHitRect(scrollBounds)
            return editText.getLocalVisibleRect(scrollBounds) && scrollBounds.height() >= editText.height
        }
    }

    abstract inner class StructuredNameWrapper(@IdRes id: Int, extractFromStructuredName: (StructuredName) -> String?) : EditTextWrapper(id) {
        init {
            viewModel.getStructuredName().observe(this@EditSendContactActivity) { name ->
                val text = extractFromStructuredName(name)
                if (text != null) {
                    setInitialText(text)
                }
            }
        }
    }

    /**
     * Manages the edit text of the name prefixes. Pass null as initial text if the name prefix
     * is empty in the given vcard.
     */
    inner class NamePrefixWrapper(@IdRes id: Int) : StructuredNameWrapper(
            id,
            { n -> n.prefixes?.joinToString(" ")?.trim()?.let { if (it == "") null else it } }
    ) {

        override fun onTextChanged(text: String) {
            viewModel.getStructuredName().value?.prefixes?.clear()
            viewModel.getStructuredName().value?.prefixes?.add(text)
        }
    }

    /**
     * Manages the edit text of the first name.
     */
    inner class FirstNameWrapper(@IdRes id: Int) : StructuredNameWrapper(
            id,
            { n -> n.given ?: "" }
    ) {
        override fun onTextChanged(text: String) {
            viewModel.getStructuredName().value?.given = text
        }
    }

    /**
     * Manages the edit text of the middle names.
     */
    inner class MiddleNameWrapper(@IdRes id: Int) : StructuredNameWrapper(
            id,
            { n -> n.additionalNames?.joinToString("")?.trim()?.let { if (it == "") null else it } }
    ) {
        override fun onTextChanged(text: String) {
            viewModel.getStructuredName().value?.additionalNames?.clear()
            viewModel.getStructuredName().value?.additionalNames?.add(text)
        }
    }

    /**
     * Manages the edit text of the last name.
     */
    inner class LastNameWrapper(@IdRes id: Int) : StructuredNameWrapper(
            id,
            { n -> n.family ?: "" }
    ) {
        override fun onTextChanged(text: String) {
            viewModel.getStructuredName().value?.family = text
        }
    }

    /**
     * Manages the edit text of the name suffixes.
     */
    inner class NameSuffixWrapper(@IdRes id: Int) : StructuredNameWrapper(
            id,
            { n ->
                n.suffixes?.joinToString("")?.trim()?.let {
                    if (it == "") null else it
                }
            }
    ) {
        override fun onTextChanged(text: String) {
            viewModel.getStructuredName().value?.suffixes?.clear()
            viewModel.getStructuredName().value?.suffixes?.add(text)
        }
    }

    /**
     * Manages the edit text of the formatted name. This edit text is only shown if there is no
     * structured name and the formatted name (FN) is not null and not empty. If the structured name
     * and the formatted name are both null or empty, only the first and last name edit texts are
     * shown.
     */
    inner class FullNameWrapper(@IdRes id: Int) : EditTextWrapper(id) {

        init {
            viewModel.getFormattedName().observe(this@EditSendContactActivity) {
                setInitialText(viewModel.getFormattedName().value?.value ?: "")
            }
        }

        override fun onTextChanged(text: String) {
            viewModel.getFormattedName().value?.value = text
        }
    }

    companion object {
        const val EXTRA_CONTACT = "EXTRA_CONTACT"
        const val RESULT_CONTACT_URI = "CONTACT_URI"
        const val RESULT_CONTACT_NAME = "CONTACT_NAME"
    }
}

