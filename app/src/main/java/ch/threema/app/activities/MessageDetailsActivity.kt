/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.compose.edithistory.EditHistoryList
import ch.threema.app.compose.edithistory.EditHistoryViewModel
import ch.threema.app.compose.message.CompleteMessageBubble
import ch.threema.app.compose.message.MessageDetailsListBox
import ch.threema.app.compose.message.MessageTimestampsListBox
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.listeners.EditMessageListener
import ch.threema.app.listeners.MessageDeletedForAllListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.ui.CustomTextSelectionCallback
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.LinkifyUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import com.google.android.material.appbar.MaterialToolbar
import org.slf4j.Logger

class MessageDetailsActivity : ThreemaToolbarActivity(), DialogClickListener {

    private companion object {
        val logger: Logger = LoggingUtil.getThreemaLogger("MessageDetailsActivity")

        const val CONTEXT_MENU_FORWARD = 600
        const val CONTEXT_MENU_GROUP = 22200
    }

    private lateinit var toolbar: MaterialToolbar

    private val viewModel: MessageDetailsViewModel by viewModels<MessageDetailsViewModel> {
        MessageDetailsViewModel.provideFactory(
            IntentDataUtil.getAbstractMessageId(intent),
            IntentDataUtil.getAbstractMessageType(intent),
            serviceManager.userService.identity,
        )
    }

    private val onEditMessageListener = object : EditMessageListener {
        override fun onEdit(message: AbstractMessageModel) {
            if (message.uid == viewModel.uiState.value.message.uid) {
                viewModel.refreshMessage(message)
            }
        }
    }

    private val onDeleteMessageListener = object : MessageDeletedForAllListener {
        override fun onDeletedForAll(message: AbstractMessageModel) {
            if (message.uid == viewModel.uiState.value.message.uid) {
                viewModel.refreshMessage(message)
            }
        }
    }

    private val textSelectionCallback: CustomTextSelectionCallback = object : CustomTextSelectionCallback() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.removeGroup(CONTEXT_MENU_GROUP)
            try {
                if (textView != null) {
                    menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_FORWARD, 200, R.string.forward_text)
                }
            } catch (e: Exception) {
                // some MIUI devices crash when attempting to add a context menu
                logger.error("Error adding context menu (Xiaomi?)", e)
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                CONTEXT_MENU_FORWARD -> forwardText()
                else -> return false
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            // we ignore this
        }

        private fun forwardText() {
            val textView = textView ?: return
            val text = textView.text

            if (text.isNotEmpty()) {
                val start = textView.selectionStart
                val end = textView.selectionEnd

                val body = text.subSequence(start, end).toString()
                val intent = Intent(
                    this@MessageDetailsActivity,
                    RecipientListBaseActivity::class.java
                )
                intent.setType("text/plain")
                intent.setAction(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_TEXT, body)
                intent.putExtra(ThreemaApplication.INTENT_DATA_IS_FORWARD, true)
                startActivity(intent)
            }
        }
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_message_details
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        theme.applyStyle(ThreemaApplication.getServiceManager()!!.preferenceService.fontStyle, true)

        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        initToolbar()
        initScreenContent()

        ListenerManager.editMessageListener.add(onEditMessageListener)
        ListenerManager.messageDeletedForAllListener.add(onDeleteMessageListener)

        return true
    }

    private fun initScreenContent() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext())
        val shouldUseDynamicColors = sharedPreferences.getBoolean("pref_dynamic_color", false)

        val editHistoryComposeView = findViewById<ComposeView>(R.id.message_details_compose_view)
        editHistoryComposeView.setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)

        editHistoryComposeView.setContent {
            ThreemaTheme(
                dynamicColor = shouldUseDynamicColors
            ) {
                val uiState: ChatMessageDetailsUiState by viewModel.uiState.collectAsStateWithLifecycle()
                val messageModel: MessageUiModel = uiState.message
                val editHistoryViewModel: EditHistoryViewModel = viewModel(
                    key = messageModel.uid,
                    factory = EditHistoryViewModel.provideFactory(messageModel.uid)
                )
                val editHistoryUiState by editHistoryViewModel.editHistoryUiState.collectAsStateWithLifecycle()

                EditHistoryList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    editHistoryUiState = editHistoryUiState,
                    isOutbox = messageModel.isOutbox,
                    shouldMarkupText = uiState.shouldMarkupText,
                    headerContent = {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            CompleteMessageBubble(
                                message = messageModel,
                                shouldMarkupText = uiState.shouldMarkupText,
                                isTextSelectable = true,
                                textSelectionCallback = textSelectionCallback
                            )
                        }
                    },
                    footerContent = if (BuildConfig.SHOW_TIMESTAMPS_AND_TECHNICAL_INFO_IN_MESSAGE_DETAILS) {
                        {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                MessageTimestampsListBox(
                                    messageTimestampsUiModel = messageModel.messageTimestampsUiModel,
                                    isOutbox = messageModel.isOutbox
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MessageDetailsListBox(
                                    messageDetailsUiModel = messageModel.messageDetailsUiModel,
                                    isOutbox = messageModel.isOutbox
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    } else {
                        {}
                    }
                )
            }
        }
    }

    private fun initToolbar() {
        toolbar = findViewById(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.enable_formatting) {
                onToggleFormattingClicked(item)
                return@setOnMenuItemClickListener true
            }
            false
        }
        toolbar.setTitle(getString(R.string.message_log_title))
        ConfigUtils.addIconsToOverflowMenu(this, toolbar.getMenu())
    }

    private fun onToggleFormattingClicked(item: MenuItem) {
        item.setChecked(!item.isChecked)
        viewModel.markupText(item.isChecked)
    }

    override fun onDestroy() {
        ListenerManager.editMessageListener.remove(onEditMessageListener)
        ListenerManager.messageDeletedForAllListener.remove(onDeleteMessageListener)
        super.onDestroy()
    }

    override fun onYes(tag: String, data: Any) {
        if (LinkifyUtil.DIALOG_TAG_CONFIRM_LINK == tag) {
            LinkifyUtil.getInstance().openLink(data as Uri, null, this)
        }
    }

    override fun onNo(tag: String, data: Any) {
        //
    }
}
