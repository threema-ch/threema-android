package ch.threema.app.usecases

import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService

class OverrideOneTimeHintsUseCase(
    private val preferenceService: PreferenceService,
) {
    @JvmOverloads
    fun call(dismiss: Boolean = true) {
        with(preferenceService) {
            setGroupCallsTooltipShown(dismiss)
            setFileSendInfoShown(dismiss)
            setIsWorkHintTooltipShown(dismiss)
            setFaceBlurTooltipShown(dismiss)
            setMultipleRecipientsTooltipShown(dismiss)
            setVideoCallToggleTooltipShown(dismiss)
            setOneTimeDialogShown("note_group_hint", dismiss)
            setOneTimeDialogShown("individual_confirm", dismiss)
            setTooltipPopupDismissed(R.string.preferences__tooltip_emoji_reactions_shown, dismiss)
            setTooltipPopupDismissed(R.string.preferences__tooltip_export_id_shown, dismiss)
            setTooltipPopupDismissed(R.string.preferences__tooltip_audio_selector_hint, dismiss)
            setTooltipPopupDismissed(R.string.preferences__tooltip_gc_camera, dismiss)
        }
    }
}
