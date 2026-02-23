package ch.threema.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.DefaultLifecycleObserver
import ch.threema.app.R
import ch.threema.app.dialogs.TextWithCheckboxDialog
import ch.threema.app.utils.NameUtil
import ch.threema.storage.models.ContactModel
import com.google.android.material.button.MaterialButton

class ReportSpamView : ConstraintLayout, DefaultLifecycleObserver {
    private var listener: OnReportButtonClickListener? = null
    private var contactModel: ContactModel? = null

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        init(context)
    }

    private fun init(context: Context) {
        if (getContext() !is AppCompatActivity) {
            return
        }
        activity.lifecycle.addObserver(this)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.notice_report_spam, this)
    }

    fun setListener(listener: OnReportButtonClickListener?) {
        this.listener = listener
    }

    @UiThread
    fun show(contactModel: ContactModel) {
        this.contactModel = contactModel
        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }
    }

    @UiThread
    fun hide() {
        if (visibility != GONE) {
            visibility = GONE
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val reportButton: MaterialButton = findViewById(R.id.button_report_spam)
        reportButton.setOnClickListener {
            val dialog = TextWithCheckboxDialog.newInstance(
                context.getString(
                    R.string.spam_report_dialog_title,
                    NameUtil.getDisplayNameOrNickname(contactModel, true),
                ),
                R.string.spam_report_dialog_explain,
                R.string.spam_report_dialog_block_checkbox,
                R.string.spam_report_short,
                R.string.cancel,
            )
            dialog.setCallback { _: String?, _: Any?, checked: Boolean ->
                listener!!.onReportSpamClicked(
                    contactModel!!,
                    checked,
                )
            }
            dialog.show(activity.supportFragmentManager, "")
        }
    }

    private val activity: AppCompatActivity
        get() = context as AppCompatActivity

    interface OnReportButtonClickListener {
        fun onReportSpamClicked(spammerContactModel: ContactModel, block: Boolean)
    }
}
