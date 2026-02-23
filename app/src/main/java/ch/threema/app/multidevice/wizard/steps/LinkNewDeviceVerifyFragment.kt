package ch.threema.app.multidevice.wizard.steps

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ch.threema.app.R
import ch.threema.app.multidevice.wizard.LinkingResult
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.google.android.material.button.MaterialButton

private val logger = getThreemaLogger("LinkNewDeviceVerifyFragment")

class LinkNewDeviceVerifyFragment : LinkNewDeviceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private val emojiSelectionViews = arrayOfNulls<LinkNewDeviceEmojiSelectionView>(3)

    private val onValidSelectionListener = View.OnClickListener {
        returnData(true)
    }

    private val onInvalidSelectionListener = View.OnClickListener {
        setupEmojiSelectionViews()
    }

    private val onNoMatchListener = View.OnClickListener {
        returnData(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_link_new_device_verify, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.info_text).text = getString(R.string.trust_new_device_info, getString(R.string.app_name))

        emojiSelectionViews[0] = view.findViewById(R.id.emoji_selection1)
        emojiSelectionViews[1] = view.findViewById(R.id.emoji_selection2)
        emojiSelectionViews[2] = view.findViewById(R.id.emoji_selection3)

        emojiSelectionViews[0]?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)
        emojiSelectionViews[1]?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)
        emojiSelectionViews[2]?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        view.findViewById<View>(R.id.parent_layout).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.vertical(),
        )

        view.findViewById<MaterialButton>(R.id.no_match_button).setOnClickListener(onNoMatchListener)

        setupEmojiSelectionViews()
    }

    private fun setupEmojiSelectionViews() {
        val validViewIndex = (emojiSelectionViews.indices).random()

        repeat(emojiSelectionViews.size) { i ->
            emojiSelectionViews[i]?.let {
                if (i == validViewIndex) {
                    it.setEmojis(
                        intArrayOf(
                            viewModel.deviceLinkingStatusConnected.emojiIndices.first,
                            viewModel.deviceLinkingStatusConnected.emojiIndices.second,
                            viewModel.deviceLinkingStatusConnected.emojiIndices.third,
                        ),
                    )
                    it.setOnClickListener(onValidSelectionListener)
                } else {
                    it.setRandomEmojis()
                    it.setOnClickListener(onInvalidSelectionListener)
                }
            }
        }
    }

    private fun returnData(success: Boolean) {
        try {
            if (success) {
                viewModel.deviceLinkingStatusConnected.confirmRendezvousPath()
                viewModel.showLinkingProgress()
            } else {
                viewModel.deviceLinkingStatusConnected.declineRendezvousPath()
                viewModel.showResultFailure(LinkingResult.Failure.EmojiMismatch)
            }
        } catch (e: Exception) {
            viewModel.showResultFailure(LinkingResult.Failure.Unexpected)
        }
    }
}
