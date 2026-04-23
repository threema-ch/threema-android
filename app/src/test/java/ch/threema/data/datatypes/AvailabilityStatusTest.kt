package ch.threema.data.datatypes

import ch.threema.base.utils.Base64
import ch.threema.protobuf.d2d.sync.WorkAvailabilityStatusCategory
import ch.threema.protobuf.d2d.sync.workAvailabilityStatus
import ch.threema.storage.DbAvailabilityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import testdata.TestData

class AvailabilityStatusTest {

    @Test
    fun toDatabaseModel() {
        val dbModelNone = AvailabilityStatus.None.toDatabaseModel(
            identity = TestData.Identities.OTHER_1.value,
        )
        assertEquals(
            expected = DbAvailabilityStatus(
                identity = TestData.Identities.OTHER_1.value,
                category = 0,
                description = "",
            ),
            actual = dbModelNone,
        )

        val dbModelUnavailable = AvailabilityStatus
            .Unavailable(
                description = "On vacation",
            )
            .toDatabaseModel(
                identity = TestData.Identities.OTHER_1.value,
            )
        assertEquals(
            expected = DbAvailabilityStatus(
                identity = TestData.Identities.OTHER_1.value,
                category = 1,
                description = "On vacation",
            ),
            actual = dbModelUnavailable,
        )

        val dbModelUnavailableNoDesc = AvailabilityStatus
            .Unavailable(
                description = "",
            )
            .toDatabaseModel(
                identity = TestData.Identities.OTHER_1.value,
            )
        assertEquals(
            expected = DbAvailabilityStatus(
                identity = TestData.Identities.OTHER_1.value,
                category = 1,
                description = "",
            ),
            actual = dbModelUnavailableNoDesc,
        )

        val dbModelBusy = AvailabilityStatus
            .Busy(
                description = "In a meeting",
            )
            .toDatabaseModel(
                identity = TestData.Identities.OTHER_1.value,
            )
        assertEquals(
            expected = DbAvailabilityStatus(
                identity = TestData.Identities.OTHER_1.value,
                category = 2,
                description = "In a meeting",
            ),
            actual = dbModelBusy,
        )

        val dbModelBusyNoDesc = AvailabilityStatus
            .Busy(
                description = "",
            )
            .toDatabaseModel(
                identity = TestData.Identities.OTHER_1.value,
            )
        assertEquals(
            expected = DbAvailabilityStatus(
                identity = TestData.Identities.OTHER_1.value,
                category = 2,
                description = "",
            ),
            actual = dbModelBusyNoDesc,
        )
    }

    @Test
    fun fromDatabaseValues() {
        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 0,
                description = "",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 0,
                description = "ignored",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Unavailable(
                description = "On vacation",
            ),
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 1,
                description = "On vacation",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Unavailable(
                description = "",
            ),
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 1,
                description = "",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Busy(
                description = "In a meeting",
            ),
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 2,
                description = "In a meeting",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Busy(
                description = "",
            ),
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 2,
                description = "",
            ),
        )
        assertNull(
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = 3,
                description = "",
            ),
        )
        assertNull(
            actual = AvailabilityStatus.fromDatabaseValues(
                categorySerialized = -1,
                description = "",
            ),
        )
    }

    @Test
    fun toProtocolModel() {
        val syncModelNone = AvailabilityStatus.None.toProtocolModel()
        assertEquals(
            expected = 0,
            actual = syncModelNone.categoryValue,
        )
        assertEquals(
            expected = "",
            actual = syncModelNone.description,
        )

        val syncModelUnavailable = AvailabilityStatus.Unavailable(description = "On vacation").toProtocolModel()
        assertEquals(
            expected = 1,
            actual = syncModelUnavailable.categoryValue,
        )
        assertEquals(
            expected = "On vacation",
            actual = syncModelUnavailable.description,
        )

        val syncModelBusy = AvailabilityStatus.Busy(description = "In a meeting").toProtocolModel()
        assertEquals(
            expected = 2,
            actual = syncModelBusy.categoryValue,
        )
        assertEquals(
            expected = "In a meeting",
            actual = syncModelBusy.description,
        )
    }

    @Test
    fun fromProtocolModel() {
        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {},
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.NONE
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.NONE
                    description = ""
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.NONE
                    description = "ignored"
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Unavailable(),
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.UNAVAILABLE
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Unavailable(),
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.UNAVAILABLE
                    description = ""
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Unavailable(
                description = "On vacation",
            ),
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.UNAVAILABLE
                    description = "On vacation"
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Busy(),
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.BUSY
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Busy(),
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.BUSY
                    description = ""
                },
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Busy(
                description = "In a meeting",
            ),
            actual = AvailabilityStatus.fromProtocolModel(
                workAvailabilityStatus {
                    category = WorkAvailabilityStatusCategory.BUSY
                    description = "In a meeting"
                },
            ),
        )
    }

    @Test
    fun fromProtocolBase64() {
        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = "",
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {}.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.NONE
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.NONE
                        description = ""
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Unavailable(),
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.UNAVAILABLE
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Unavailable(),
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.UNAVAILABLE
                        description = ""
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Unavailable(
                description = "On vacation",
            ),
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.UNAVAILABLE
                        description = "On vacation"
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Busy(),
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.BUSY
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Busy(),
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.BUSY
                        description = ""
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = AvailabilityStatus.Busy(
                description = "In a meeting",
            ),
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = Base64.encodeBytes(
                    workAvailabilityStatus {
                        category = WorkAvailabilityStatusCategory.BUSY
                        description = "In a meeting"
                    }.toByteArray(),
                ),
            ),
        )

        assertEquals(
            expected = null,
            actual = AvailabilityStatus.fromProtocolBase64(
                workAvailabilityStatusBase64 = "invalid data",
            ),
        )
    }

    @Test
    fun toJson() {
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.None"}""",
            actual = AvailabilityStatus.None.toJson(),
        )
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unavailable"}""",
            actual = AvailabilityStatus.Unavailable().toJson(),
        )
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unavailable"}""",
            actual = AvailabilityStatus.Unavailable(description = "").toJson(),
        )
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unavailable","description":"On vacation"}""",
            actual = AvailabilityStatus.Unavailable(description = "On vacation").toJson(),
        )
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Busy"}""",
            actual = AvailabilityStatus.Busy().toJson(),
        )
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Busy"}""",
            actual = AvailabilityStatus.Busy(description = "").toJson(),
        )
        assertEquals(
            expected = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Busy","description":"In a meeting"}""",
            actual = AvailabilityStatus.Busy(description = "In a meeting").toJson(),
        )
    }

    @Test
    fun fromJson() {
        assertEquals(
            expected = AvailabilityStatus.None,
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.None"}""",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Unavailable(),
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unavailable"}""",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Unavailable(description = ""),
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unavailable"}""",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Unavailable(description = "On vacation"),
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unavailable","description":"On vacation"}""",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Busy(),
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Busy"}""",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Busy(description = ""),
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Busy"}""",
            ),
        )
        assertEquals(
            expected = AvailabilityStatus.Busy(description = "In a meeting"),
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Busy","description":"In a meeting"}""",
            ),
        )
        assertNull(
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = "",
            ),
        )
        assertNull(
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = "{}",
            ),
        )
        assertNull(
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"String"}""",
            ),
        )
        assertNull(
            actual = AvailabilityStatus.fromJson(
                availabilityStatusJson = """{"type":"ch.threema.data.datatypes.AvailabilityStatus.Unknown","description":"In a meeting"}""",
            ),
        )
    }
}
