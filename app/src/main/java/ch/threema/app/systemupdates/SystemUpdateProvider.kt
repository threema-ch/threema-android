package ch.threema.app.systemupdates

import ch.threema.app.systemupdates.updates.*

class SystemUpdateProvider {
    fun getUpdates(oldVersion: Int): List<SystemUpdate> {
        if (oldVersion == version) {
            return emptyList()
        }
        return updates
            .asReversed()
            .asSequence()
            .map { updateConstructor ->
                updateConstructor.invoke()
            }
            .takeWhile { update ->
                oldVersion < update.version
            }
            .toList()
            .reversed()
    }

    private val updates: List<() -> SystemUpdate>
        get() = listOf(
            ::SystemUpdateToVersion12,
            ::SystemUpdateToVersion14,
            ::SystemUpdateToVersion31,
            ::SystemUpdateToVersion39,
            ::SystemUpdateToVersion40,
            ::SystemUpdateToVersion42,
            ::SystemUpdateToVersion43,
            ::SystemUpdateToVersion46,
            ::SystemUpdateToVersion48,
            ::SystemUpdateToVersion53,
            ::SystemUpdateToVersion54,
            ::SystemUpdateToVersion55,
            ::SystemUpdateToVersion63,
            ::SystemUpdateToVersion64,
            ::SystemUpdateToVersion66,
            ::SystemUpdateToVersion72,
            ::SystemUpdateToVersion91,
            ::SystemUpdateToVersion110,
            ::SystemUpdateToVersion111,
            ::SystemUpdateToVersion112,
            ::SystemUpdateToVersion113,
            ::SystemUpdateToVersion114,
            ::SystemUpdateToVersion115,
            ::SystemUpdateToVersion117,
            ::SystemUpdateToVersion118,
            ::SystemUpdateToVersion119,
            ::SystemUpdateToVersion120,
            ::SystemUpdateToVersion121,
            ::SystemUpdateToVersion122,
            ::SystemUpdateToVersion123,
            ::SystemUpdateToVersion124,
            ::SystemUpdateToVersion125,
            ::SystemUpdateToVersion126,
        )

    val version = 126
}
