/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.utils.LinkifyUtil

class WorkIntroActivity : ThreemaActivity() {

    companion object {
        private const val HTML_LINK_FORMAT_TEMPLATE_PREFIX = "<a href='%1\$s'> "
        private const val HTML_LINK_FORMAT_TEMPLATE_POSTFIX = " </a>"
    }

    private val loginButton: Button by lazy { findViewById(R.id.work_intro_login_button) }
    private val consumerStoreButton: Button by lazy { findViewById(R.id.consumer_notice_app_store_button) }
    private val workInfoLinkTextView: TextView by lazy { findViewById(R.id.work_intro_more_link) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_work_intro)

        initLoginButton()
        initWorkInfoLink()
        initConsumerStoreButton()
    }

    private fun initLoginButton() {
        loginButton.text = getString(R.string.work_intro_login).format(getString(R.string.app_name))
        loginButton.setOnClickListener {
            val intent = Intent(this, EnterSerialActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initWorkInfoLink() {
        val workInfoLinkTemplate = HTML_LINK_FORMAT_TEMPLATE_PREFIX
            .plus(getString(R.string.work_intro_more_link_text))
            .plus(HTML_LINK_FORMAT_TEMPLATE_POSTFIX)

        val workInfoLink = getString(R.string.threema_work_url)

        val workInfoHtmlLink = String.format(
            workInfoLinkTemplate,
            workInfoLink
        )

        workInfoLinkTextView.text =
            HtmlCompat.fromHtml(workInfoHtmlLink, HtmlCompat.FROM_HTML_MODE_COMPACT)
        workInfoLinkTextView.movementMethod = LinkMovementMethod.getInstance()
        workInfoLinkTextView.setOnClickListener {
            LinkifyUtil.getInstance().openLink(Uri.parse(workInfoLink), null, this)
        }
    }

    private fun initConsumerStoreButton() {
        val appStoreText: String
        val onButtonClick: () -> Unit

        if (BuildFlavor.current.licenseType == BuildFlavor.LicenseType.HMS_WORK) {
            appStoreText = getString(R.string.consumer_notice_appgallery_link_description)
            onButtonClick = { openConsumerAppInHuaweiAppGallery() }
        } else {
            appStoreText = getString(R.string.consumer_notice_playstore_link_description)
            onButtonClick = { openConsumerAppInPlayStore() }
        }

        consumerStoreButton.text = appStoreText
        consumerStoreButton.setOnClickListener {
            try {
                onButtonClick()
            } catch (e: Exception) {
                Toast
                    .makeText(this, getString(R.string.no_activity_for_intent), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun openConsumerAppInPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(getString(R.string.private_download_url)))
        intent.setPackage("com.android.vending")
        startActivity(intent)
    }

    private fun openConsumerAppInHuaweiAppGallery() {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = Uri.parse("market://details?id=" + this.packageName)
        intent.setData(uri)
        intent.setPackage("com.huawei.appmarket")
        startActivity(intent)
    }
}
