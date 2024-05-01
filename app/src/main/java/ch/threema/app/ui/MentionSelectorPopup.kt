/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager.BadTokenException
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.window.layout.WindowMetricsCalculator
import ch.threema.app.R
import ch.threema.app.adapters.MentionSelectorAdapter
import ch.threema.app.collections.Functional
import ch.threema.app.collections.IPredicateNonNull
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AnimationUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.TestUtil
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

@SuppressLint("InflateParams")
class MentionSelectorPopup(
    private val context: Context,
    private val mentionSelectorListener: MentionSelectorListener?,
    private val groupService: GroupService,
    private val contactService: ContactService,
    private val userService: UserService,
    private val preferenceService: PreferenceService,
    private val groupModel: GroupModel
) : MovingPopupWindow(context), MentionSelectorAdapter.OnClickListener {
    private var mentionAdapter: MentionSelectorAdapter? = null
    private var filterText: String = ""
    private var filterStart: Int = 0
    private val recyclerView: RecyclerView
    private val allContactModel: ContactModel =
        ContactModel(ContactService.ALL_USERS_PLACEHOLDER_ID, byteArrayOf())
    private var editText: ComposeEditText? = null
    private val popupLayout: MaterialCardView
    private var viewableSpaceHeight = 0
    private var overlayMode = false // whether this popup is shown on top of another popupwindow
    private val textWatcher: TextWatcher = object : TextWatcher {
        private fun run() {
            dismiss()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            try {
                if (count == 0 && start == 0) { // @ at first position is deleted
                    editText?.post { this.run() }
                    return
                }
                val last = s[start - 1]
                if (count == 0 && (' ' == last || '\n' == last) || count == 1 && (' ' == s[start] || '\n' == s[start])) { // if spacebar or newline is added, escape the mention popup.
                    editText?.post { this.run() }
                }
            } catch (e: IndexOutOfBoundsException) {
                // don't care, happens when deleting a char after the @ the first time around
                // hacky because there is no other logic with the listener callback that would not mess with the rest of the logic.
            }
        }

        override fun afterTextChanged(s: Editable) {
            if (TextUtils.isEmpty(s)) { // if text field is completely empty
                editText?.post { this.run() }
            } else if (s.toString() != filterText) {
                val filterTextAfterAtChar: String?
                var spacePosition = -1
                try {
                    filterTextAfterAtChar = s.toString().substring(filterStart)
                    if (!TestUtil.empty(filterTextAfterAtChar)) {
                        spacePosition = filterTextAfterAtChar.indexOf(" ")
                        if (spacePosition == -1) {
                            spacePosition = filterTextAfterAtChar.indexOf("\n")
                        }
                    }
                } catch (e: IndexOutOfBoundsException) {
                    //
                }
                filterText = if (spacePosition != -1) {
                    s.toString().substring(0, filterStart + spacePosition)
                } else {
                    s.toString()
                }
                updateList(false)
                updateRecyclerViewDimensions()
            }
        }
    }

    init {
        popupLayout = LayoutInflater.from(context).inflate(R.layout.popup_mention_selector, null, false) as MaterialCardView
        contentView = popupLayout
        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        animationStyle = 0
        isFocusable = false
        isTouchable = true
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        setWindowLayoutMode(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        height = 1

        allContactModel.setName(context.getString(R.string.all), "")
        allContactModel.state = ContactModel.State.ACTIVE
        filterText = ""
        filterStart = 0

        recyclerView = popupLayout.findViewById(R.id.group_members_list)
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.stackFromEnd = true
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.itemAnimator = null

        val adapter = updateList(true)
        if (adapter != null) {
            recyclerView.adapter = adapter
        }
    }

    fun show(activity: Activity, editText: ComposeEditText, anchorView: TextInputLayout?) {
        super.show(activity, anchorView)

        if (mentionAdapter == null) {
            dismiss()
            return
        }

        if (anchorView != null) {
            popupLayout.setCardBackgroundColor(anchorView.boxBackgroundColor)
            overlayMode =
                if (anchorView.boxCornerRadiusTopStart == anchorView.resources.getDimensionPixelSize(R.dimen.compose_textinputlayout_radius_expanded)
                        .toFloat()
                ) {
                    true
                } else {
                    anchorView.setBoxCornerRadiiResources(
                        R.dimen.compose_textinputlayout_radius_expanded,
                        R.dimen.compose_textinputlayout_radius_expanded,
                        R.dimen.compose_textinputlayout_radius,
                        R.dimen.compose_textinputlayout_radius
                    )
                    false
                }
        }

        val coordinates = ConfigUtils.getPopupWindowPositionAboveAnchor(activity, anchorView ?: editText)
        val popupX = if (anchorView == null) 0 else coordinates[0]
        var popupY = coordinates[1]

        if (anchorView == null) {
            popupY += context.resources.getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_vertical)
        }

        this.editText = editText
        editText.setLocked(true)
        editText.addTextChangedListener(textWatcher)
        filterStart = editText.selectionStart
        viewableSpaceHeight = coordinates[2] - context.resources.getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_vertical)
        this.width =
            if (anchorView == null) WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds.width()
            else editText.width
        this.height = viewableSpaceHeight

        try {
            showAtLocation(editText, Gravity.LEFT or Gravity.BOTTOM, popupX, popupY)
            contentView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    AnimationUtil.slideInAnimation(contentView, true, 150)
                }
            })
            this.anchorView?.let {
                ViewCompat.setWindowInsetsAnimationCallback(
                    it,
                    windowInsetsAnimationCallback
                )
                it.addOnLayoutChangeListener(onLayoutChangeListener)
            }
        } catch (e: BadTokenException) {
            //
        }
    }

    private fun updateRecyclerViewDimensions() {
        val maxHeight = context.resources.getDimensionPixelSize(R.dimen.group_detail_list_item_size) * (mentionAdapter?.itemCount ?: 1)
        recyclerView.layoutParams.height = maxHeight.coerceAtMost(viewableSpaceHeight)
        recyclerView.requestLayout()
    }

    private fun updateList(init: Boolean): MentionSelectorAdapter? {
        var groupContacts = contactService.getByIdentities(
            groupService.getGroupIdentities(groupModel)
        )
        val isSortingFirstName = preferenceService.isContactListSortingFirstName

        groupContacts.sortWith { model1: ContactModel?, model2: ContactModel? ->
            ContactUtil.getSafeNameString(model1, isSortingFirstName).compareTo(
                ContactUtil.getSafeNameString(model2, isSortingFirstName)
            )
        }
        groupContacts.add(allContactModel)

        if (!init && filterText.length - filterStart > 0) {
            groupContacts = Functional.filter(groupContacts,
                IPredicateNonNull { contactModel: ContactModel ->
                    val lowercaseName =
                        filterText.substring(filterStart).lowercase(Locale.getDefault())
                    if (userService.isMe(contactModel.identity) && NameUtil.getQuoteName(
                            contactModel,
                            userService
                        ).lowercase(Locale.getDefault()).contains(lowercaseName)
                    ) {
                        return@IPredicateNonNull true
                    }
                    ContactUtil.getSafeNameString(contactModel, isSortingFirstName)
                        .lowercase(Locale.getDefault()).contains(lowercaseName)
                })
        }

        if (groupContacts.isEmpty()) { // just show all selector as default placeholder if there are no more specific results
            groupContacts.add(allContactModel)
        }

        if (mentionAdapter == null) {
            mentionAdapter = MentionSelectorAdapter(context, userService, contactService, groupService, groupModel)
            mentionAdapter?.setOnClickListener(this)
        }

        mentionAdapter?.setData(groupContacts)

        return mentionAdapter
    }

    override fun onItemClick(v: View, contactModel: ContactModel) {
        val identity = contactModel.identity
        if (mentionSelectorListener != null) {
            dismiss()
            mentionSelectorListener.onContactSelected(
                identity,
                filterText.length - filterStart + 1,
                if (filterStart > 0) filterStart - 1 else 0
            )
        }
    }

    override fun dismiss() {
        anchorView?.let {
            if (!overlayMode) {
                it.setBoxCornerRadiiResources(
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius
                )
                it.removeOnLayoutChangeListener(onLayoutChangeListener)
                ViewCompat.setWindowInsetsAnimationCallback(it, null)
            }
        }

        editText?.let {
            it.removeTextChangedListener(textWatcher)
            it.setLocked(false)
        }
        super.dismiss()
    }

    interface MentionSelectorListener {
        fun onContactSelected(identity: String?, length: Int, insertPosition: Int)
    }
}
