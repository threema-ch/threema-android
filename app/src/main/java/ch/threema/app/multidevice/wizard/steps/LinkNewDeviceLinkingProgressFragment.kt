package ch.threema.app.multidevice.wizard.steps

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ch.threema.app.R
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("LinkNewDeviceLinkingProgressFragment")

class LinkNewDeviceLinkingProgressFragment : LinkNewDeviceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private lateinit var bodyTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_link_new_device_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bodyTextView = view.findViewById(R.id.body)
        view.findViewById<LinearLayout>(R.id.explain_layout).layoutTransition.enableTransitionType(
            LayoutTransition.CHANGING,
        )

        view.findViewById<View>(R.id.parent_layout).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.vertical(),
        )

        viewModel.linkingProgressStringRes.observe(viewLifecycleOwner) { stringRes ->
            bodyTextView.text = getString(stringRes)
        }
    }
}
