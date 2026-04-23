package ch.threema.app.availabilitystatus.edit

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.usecases.availabilitystatus.UpdateUserAvailabilityStatusUseCase
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.protocol.csp.ProtocolDefines

class EditAvailabilityStatusViewModel(
    private val preferenceService: PreferenceService,
    private val updateUserAvailabilityStatusUseCase: UpdateUserAvailabilityStatusUseCase,
) : BaseViewModel<EditAvailabilityStatusState, EditAvailabilityStatusEvent>() {

    override fun initialize() = runInitialization {
        val currentAvailabilityStatus = getCurrentAvailabilityStatusOrNone()
        EditAvailabilityStatusState(
            status = currentAvailabilityStatus,
            descriptionState = AvailabilityStatusDescriptionState.create(
                description = when (currentAvailabilityStatus) {
                    AvailabilityStatus.None -> ""
                    is AvailabilityStatus.Set -> currentAvailabilityStatus.description
                },
            ),
            isLoading = false,
            hasError = false,
        )
    }

    fun onClickStatus(availabilityStatus: AvailabilityStatus) = runAction {
        updateViewState {
            copy(
                status = when (availabilityStatus) {
                    AvailabilityStatus.None -> availabilityStatus
                    is AvailabilityStatus.Busy -> AvailabilityStatus.Busy(
                        description = currentViewState.descriptionState.description,
                    )
                    is AvailabilityStatus.Unavailable -> AvailabilityStatus.Unavailable(
                        description = currentViewState.descriptionState.description,
                    )
                },
                hasError = false,
            )
        }
    }

    fun onChangeDescription(description: String) = runAction {
        val updatedStatus =
            when (val selectedStatus = currentViewState.status) {
                AvailabilityStatus.None -> selectedStatus
                is AvailabilityStatus.Busy -> selectedStatus.copy(
                    description = description,
                )
                is AvailabilityStatus.Unavailable -> selectedStatus.copy(
                    description = description,
                )
            }
        updateViewState {
            copy(
                status = updatedStatus,
                descriptionState = AvailabilityStatusDescriptionState.create(
                    description = description,
                ),
                hasError = false,
            )
        }
    }

    fun onClickCancel() = runAction {
        emitEvent(EditAvailabilityStatusEvent.Cancel)
    }

    fun onClickSave() = runAction {
        val currentAvailabilityStatus = getCurrentAvailabilityStatusOrNone()
        if (currentAvailabilityStatus == currentViewState.status) {
            emitEvent(EditAvailabilityStatusEvent.Cancel)
            endAction()
        }

        updateViewState {
            copy(
                isLoading = true,
                hasError = false,
            )
        }
        val updateResult = updateUserAvailabilityStatusUseCase.call(
            availabilityStatus = currentViewState.status,
        )
        updateViewState {
            copy(
                isLoading = false,
                hasError = updateResult.isFailure,
            )
        }
        if (updateResult.isSuccess) {
            emitEvent(EditAvailabilityStatusEvent.Saved)
        }
    }

    private fun getCurrentAvailabilityStatusOrNone() =
        preferenceService.getAvailabilityStatus()
            ?: AvailabilityStatus.None
}

data class EditAvailabilityStatusState(
    val status: AvailabilityStatus,
    val descriptionState: AvailabilityStatusDescriptionState,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

data class AvailabilityStatusDescriptionState(
    val description: String,
    val exceedsLimit: Boolean,
) {
    companion object {
        fun create(description: String) = AvailabilityStatusDescriptionState(
            description = description,
            exceedsLimit = description.toByteArray().size > ProtocolDefines.MAX_AVAILABILITY_STATUS_DESCRIPTION_BYTES,
        )
    }
}

sealed interface EditAvailabilityStatusEvent {
    data object Cancel : EditAvailabilityStatusEvent
    data object Saved : EditAvailabilityStatusEvent
}
