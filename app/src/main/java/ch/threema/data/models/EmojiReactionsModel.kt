package ch.threema.data.models

import ch.threema.app.managers.CoreServiceManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.coroutines.flow.MutableStateFlow

class EmojiReactionsModel(
    data: List<EmojiReactionData>,
    coreServiceManager: CoreServiceManager,
) : BaseModel<List<EmojiReactionData>, Task<*, TaskCodec>>(
    modelName = "EmojiReactionModel",
    mutableData = MutableStateFlow(data),
    multiDeviceManager = coreServiceManager.multiDeviceManager,
    taskManager = coreServiceManager.taskManager,
) {
    fun addEntry(entry: EmojiReactionData) {
        if (mutableData.value?.none { it.emojiSequence == entry.emojiSequence && it.senderIdentity == entry.senderIdentity } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply {
                add(0, entry)
            }
        }
    }

    fun removeEntry(entry: EmojiReactionData) {
        if (mutableData.value?.any { it.emojiSequence == entry.emojiSequence && it.senderIdentity == entry.senderIdentity } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply {
                remove(entry)
            }
        }
    }

    fun clear() {
        mutableData.value = emptyList()
    }
}
