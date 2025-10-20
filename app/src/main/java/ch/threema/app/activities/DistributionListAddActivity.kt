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

package ch.threema.app.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.dialogs.TextEntryDialog
import ch.threema.app.dialogs.TextEntryDialog.TextEntryDialogClickListener
import ch.threema.app.services.DistributionListService
import ch.threema.app.ui.SingleToast
import ch.threema.app.utils.LogUtil
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import org.koin.android.ext.android.inject

private val logger = LoggingUtil.getThreemaLogger("DistributionListAddActivity")

class DistributionListAddActivity : MemberChooseActivity(), TextEntryDialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val distributionListService: DistributionListService by inject()

    private var distributionListModel: DistributionListModel? = null
    private var selectedContacts: List<ContactModel> = emptyList()

    private var isEdit = false

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }
        initData(savedInstanceState)

        return true
    }

    @MainThread
    override fun initData(savedInstanceState: Bundle?) {
        if (this.intent.hasExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID)) {
            this.distributionListModel = distributionListService.getById(
                this.intent.getLongExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, 0),
            )
            this.isEdit = this.distributionListModel != null
        }

        if (isEdit && savedInstanceState == null) {
            for (model in distributionListService.getDistributionListMembers(distributionListModel)) {
                preselectedIdentities.add(model.identity)
            }
        }

        if (isEdit) {
            updateToolbarSubtitle(R.string.title_select_contacts)
        } else {
            updateToolbarSubtitle(R.string.title_select_contacts)
        }

        initList()
    }

    @StringRes
    override fun getNotice(): Int = 0

    override fun getMode(): Int = MODE_DISTRIBUTION_LIST

    override fun menuNext(contacts: List<ContactModel>) {
        selectedContacts = contacts

        if (selectedContacts.isEmpty()) {
            SingleToast.getInstance().showShortText(getString(R.string.group_select_at_least_two))
            return
        }

        val distributionListName: String? =
            if (this.isEdit && this.distributionListModel != null) {
                distributionListModel!!.name
            } else {
                null
            }

        TextEntryDialog.newInstance(
            if (isEdit) R.string.title_edit_distribution_list else R.string.title_add_distribution_list,
            R.string.enter_distribution_list_name,
            R.string.ok,
            0,
            R.string.cancel,
            distributionListName,
            0,
            TextEntryDialog.INPUT_FILTER_TYPE_NONE,
            DistributionListModel.DISTRIBUTIONLIST_NAME_MAX_LENGTH_BYTES,
        ).show(supportFragmentManager, DIALOG_TAG_ENTER_NAME)
    }

    private fun launchComposeActivity() {
        val intent = Intent(this@DistributionListAddActivity, ComposeMessageActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        distributionListService.createReceiver(distributionListModel).prepareIntent(intent)

        startActivity(intent)
        finish()
    }

    // Callback from dialog "Edit distribution list - Choose a name for this list"
    override fun onYes(tag: String, text: String) {
        try {
            val selectedContactIdentities: Array<Identity> = selectedContacts.map(ContactModel::identity).toTypedArray()

            if (isEdit) {
                if (selectedContactIdentities.isNotEmpty()) {
                    distributionListService.updateDistributionList(
                        distributionListModel,
                        text,
                        selectedContactIdentities,
                    )
                }
            } else {
                distributionListModel = distributionListService.createDistributionList(
                    text,
                    selectedContactIdentities,
                )
            }
            RuntimeUtil.runOnUiThread {
                this.launchComposeActivity()
            }
        } catch (e: Exception) {
            LogUtil.exception(e, this@DistributionListAddActivity)
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun createIntent(context: Context, distributionListId: Long? = null) = buildActivityIntent<DistributionListAddActivity>(context) {
            if (distributionListId != null) {
                putExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, distributionListId)
            }
        }

        private const val DIALOG_TAG_ENTER_NAME = "enterName"
    }
}
