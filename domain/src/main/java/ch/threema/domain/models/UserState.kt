package ch.threema.domain.models

/**
 * The user's state within a group.
 */
enum class UserState(val value: Int) {
    MEMBER(0),

    KICKED(1),

    LEFT(2),

    ;

    companion object {
        @JvmStatic
        fun getByValue(value: Int): UserState? =
            entries.find { userState -> userState.value == value }
    }
}
