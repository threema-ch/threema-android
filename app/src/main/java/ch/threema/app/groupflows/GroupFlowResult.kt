package ch.threema.app.groupflows

import ch.threema.data.models.GroupModel

const val GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS: Long = 8L

sealed interface GroupFlowResult {

    data class Success(val groupModel: GroupModel) : GroupFlowResult

    sealed interface Failure : GroupFlowResult {

        data object Network : Failure

        /**
         *  Indicates an internal failure
         */
        data object Other : Failure
    }
}
