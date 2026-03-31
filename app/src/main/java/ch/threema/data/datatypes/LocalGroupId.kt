package ch.threema.data.datatypes

import ch.threema.data.models.GroupModel
import ch.threema.storage.models.group.GroupModelOld

@JvmInline
value class LocalGroupId(val id: Int)

val GroupModelOld.localGroupId: LocalGroupId
    get() = LocalGroupId(id)

val GroupModel.localGroupId: LocalGroupId
    get() = LocalGroupId(getDatabaseId().toInt())
