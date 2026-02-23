package ch.threema.lint

import ch.threema.lint.rules.LoggerName
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

@Suppress("unused")
class ThreemaLintRegistry : IssueRegistry() {
    override val issues =
        listOf(
            LoggerName.ISSUE,
        )

    override val api = CURRENT_API

    override val minApi = 6

    override val vendor = Vendor(
        vendorName = "Threema",
    )
}
