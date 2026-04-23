package ch.threema.data.datatypes

import ch.threema.base.utils.Base64
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessBlank
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.d2d.sync.WorkAvailabilityStatus
import ch.threema.protobuf.d2d.sync.WorkAvailabilityStatusCategory
import ch.threema.protobuf.d2d.sync.workAvailabilityStatus
import ch.threema.storage.DbAvailabilityStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = getThreemaLogger("AvailabilityStatus")

@Serializable
sealed interface AvailabilityStatus {

    /**
     *  @param identity The identity of a contact. The users own status is not stored in the database, so a [DbAvailabilityStatus]
     *  should never be created for the user's own status.
     */
    fun toDatabaseModel(identity: IdentityString): DbAvailabilityStatus

    fun toProtocolModel(): WorkAvailabilityStatus

    fun toLibthreemaModel(): ch.threema.libthreema.WorkAvailabilityStatus

    fun toJson(): String = Json.encodeToString(this)

    @Serializable
    data object None : AvailabilityStatus {

        override fun toDatabaseModel(identity: IdentityString): DbAvailabilityStatus =
            DbAvailabilityStatus(
                identity = identity,
                category = SERIALIZED_CATEGORY_NONE,
                description = "",
            )

        override fun toProtocolModel(): WorkAvailabilityStatus =
            workAvailabilityStatus {
                category = WorkAvailabilityStatusCategory.NONE
            }

        override fun toLibthreemaModel(): ch.threema.libthreema.WorkAvailabilityStatus =
            ch.threema.libthreema.WorkAvailabilityStatus(
                category = ch.threema.libthreema.WorkAvailabilityStatusCategory.NONE,
                description = null,
            )
    }

    @Serializable
    sealed interface Set : AvailabilityStatus {
        val description: String
    }

    @Serializable
    data class Unavailable(override val description: String = "") : Set {

        override fun toDatabaseModel(identity: IdentityString): DbAvailabilityStatus =
            DbAvailabilityStatus(
                identity = identity,
                category = SERIALIZED_CATEGORY_UNAVAILABLE,
                description = description,
            )

        override fun toProtocolModel(): WorkAvailabilityStatus =
            description.let { descriptionValue ->
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.UNAVAILABLE
                    description = descriptionValue
                }
            }

        override fun toLibthreemaModel(): ch.threema.libthreema.WorkAvailabilityStatus =
            ch.threema.libthreema.WorkAvailabilityStatus(
                category = ch.threema.libthreema.WorkAvailabilityStatusCategory.UNAVAILABLE,
                description = description.takeUnlessBlank(),
            )
    }

    @Serializable
    data class Busy(override val description: String = "") : Set {

        override fun toDatabaseModel(identity: IdentityString): DbAvailabilityStatus =
            DbAvailabilityStatus(
                identity = identity,
                category = SERIALIZED_CATEGORY_BUSY,
                description = description,
            )

        override fun toProtocolModel(): WorkAvailabilityStatus =
            description.let { descriptionValue ->
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.BUSY
                    description = descriptionValue
                }
            }

        override fun toLibthreemaModel(): ch.threema.libthreema.WorkAvailabilityStatus =
            ch.threema.libthreema.WorkAvailabilityStatus(
                category = ch.threema.libthreema.WorkAvailabilityStatusCategory.BUSY,
                description = description.takeUnlessBlank(),
            )
    }

    companion object {

        private const val SERIALIZED_CATEGORY_NONE = 0
        private const val SERIALIZED_CATEGORY_UNAVAILABLE = 1
        private const val SERIALIZED_CATEGORY_BUSY = 2

        private fun logDescriptionForCategoryNoneWarning() {
            logger.warn("An availability status of category `None` can never have a description")
        }

        @JvmStatic
        fun fromDatabaseValues(categorySerialized: Int, description: String): AvailabilityStatus? =
            when (categorySerialized) {
                SERIALIZED_CATEGORY_NONE -> {
                    if (description.isNotEmpty()) {
                        logDescriptionForCategoryNoneWarning()
                    }
                    None
                }
                SERIALIZED_CATEGORY_UNAVAILABLE -> Unavailable(description)
                SERIALIZED_CATEGORY_BUSY -> Busy(description)
                else -> {
                    logger.error("Unrecognized value of {} for AvailabilityStatus.category", categorySerialized)
                    null
                }
            }

        @JvmStatic
        fun fromProtocolBase64(workAvailabilityStatusBase64: String): AvailabilityStatus? {
            val workAvailabilityStatus =
                runCatching {
                    WorkAvailabilityStatus.parseFrom(Base64.decode(workAvailabilityStatusBase64))
                }.getOrElse { throwable ->
                    logger.error("Failed to parse availability status from base64 input", throwable)
                    null
                }
            return workAvailabilityStatus?.let(::fromProtocolModel)
        }

        @JvmStatic
        fun fromProtocolModel(workAvailabilityStatus: WorkAvailabilityStatus): AvailabilityStatus? =
            when (workAvailabilityStatus.categoryValue) {
                SERIALIZED_CATEGORY_NONE -> {
                    if (workAvailabilityStatus.description.isNotEmpty()) {
                        logDescriptionForCategoryNoneWarning()
                    }
                    None
                }
                SERIALIZED_CATEGORY_UNAVAILABLE -> Unavailable(workAvailabilityStatus.description)
                SERIALIZED_CATEGORY_BUSY -> Busy(workAvailabilityStatus.description)
                else -> {
                    logger.error("Unrecognized value of {} for WorkAvailabilityStatus.Category", workAvailabilityStatus.categoryValue)
                    null
                }
            }

        @JvmStatic
        fun fromJson(availabilityStatusJson: String): AvailabilityStatus? =
            runCatching {
                Json.decodeFromString<AvailabilityStatus>(availabilityStatusJson)
            }.getOrElse { throwable ->
                logger.error("Failed to parse availability status from json input", throwable)
                null
            }
    }
}
