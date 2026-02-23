package ch.threema.data.models

import ch.threema.app.managers.CoreServiceManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.coroutines.flow.MutableStateFlow

class EditHistoryListModel(
    data: List<EditHistoryEntryData>,
    coreServiceManager: CoreServiceManager,
) : BaseModel<List<EditHistoryEntryData>, Task<*, TaskCodec>>(
    modelName = "EditHistoryListModel",
    mutableData = MutableStateFlow(data),
    multiDeviceManager = coreServiceManager.multiDeviceManager,
    taskManager = coreServiceManager.taskManager,
) {
    fun addEntry(entry: EditHistoryEntryData) {
        if (mutableData.value?.none { it == entry } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply { add(0, entry) }
        }
    }

    fun clear() {
        mutableData.value = emptyList()
    }
}
