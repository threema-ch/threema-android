/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.data;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import ch.threema.app.managers.CoreServiceManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.storage.DatabaseBackend;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.taskmanager.TaskManager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

public class GroupModelJavaTest {
    private final DatabaseBackend databaseBackendMock = mock(DatabaseBackend.class);
    private final CoreServiceManager coreServiceManagerMock = mock(CoreServiceManager.class);
    private final MultiDeviceManager multiDeviceManagerMock = mock(MultiDeviceManager.class);
    private final TaskManager taskManagerMock = mock(TaskManager.class);

    @Before
    public void init() {
        when(coreServiceManagerMock.getMultiDeviceManager()).thenReturn(multiDeviceManagerMock);
        when(coreServiceManagerMock.getTaskManager()).thenReturn(taskManagerMock);
    }

    @Test
    public void testConstruction() {
        final String creatorIdentity = "TESTTEST";
        final GroupId groupId = new GroupId(42);
        final String name = "Group";
        final Date createdAt = new Date();
        final Date synchronizedAt = new Date();
        final Date lastUpdate = null;
        final boolean deleted = false;
        final boolean isArchived = false;
        final int colorIndex = 0;
        final String groupDesc = "Description";
        final Date groupDescChangedAt = new Date();
        final Set<String> members = Set.of("AAAAAAAA", "BBBBBBBB");
        final ch.threema.storage.models.GroupModel.UserState userState = ch.threema.storage.models.GroupModel.UserState.MEMBER;

        final GroupModel groupModel = new GroupModel(
            new GroupIdentity(creatorIdentity, groupId.toLong()),
            GroupModelData.javaCreate(
                creatorIdentity,
                groupId.toLong(),
                name,
                createdAt,
                synchronizedAt,
                lastUpdate,
                deleted,
                isArchived,
                colorIndex,
                groupDesc,
                groupDescChangedAt,
                members,
                userState
            ),
            databaseBackendMock,
            coreServiceManagerMock
        );

        final GroupModelData value = groupModel.getData().getValue();
        assertEquals("TESTTEST", value.groupIdentity.getCreatorIdentity());
        assertEquals(groupId, new GroupId(value.groupIdentity.getGroupId()));
        assertEquals(name, value.name);
        assertEquals(createdAt, value.createdAt);
        assertEquals(synchronizedAt, value.synchronizedAt);
        assertEquals(lastUpdate, value.lastUpdate);
        assertEquals(deleted, value.deleted);
        assertEquals(isArchived, value.isArchived);
        assertEquals(colorIndex, value.colorIndexInt());
        assertEquals(groupDesc, value.groupDescription);
        assertEquals(groupDescChangedAt, value.groupDescriptionChangedAt);
        assertEquals(members, value.members);
    }

    @Test
    public void testGroupIdentityByteArray() {
        String identity = "TESTTEST";
        assertArrayEquals(
            new byte[]{-42, -1, -1, -1, -1, -1, -1, -1},
            new GroupIdentity(identity, -42).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{-1, -1, -1, -1, -1, -1, -1, -1},
            new GroupIdentity(identity, -1).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{0, 0, 0, 0, 0, 0, 0, 0},
            new GroupIdentity(identity, 0).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{1, 0, 0, 0, 0, 0, 0, 0},
            new GroupIdentity(identity, 1).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{42, 0, 0, 0, 0, 0, 0, 0},
            new GroupIdentity(identity, 42).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{0, 0, 0, 0, 0, 0, 0, -128},
            new GroupIdentity(identity, Long.MIN_VALUE).getGroupIdByteArray())
        ;
        assertArrayEquals(
            new byte[]{-1, -1, -1, -1, -1, -1, -1, 127},
            new GroupIdentity(identity, Long.MAX_VALUE).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{78, -88, 120, -3, -1, -1, -1, -1},
            new GroupIdentity(identity, -42424242).getGroupIdByteArray()
        );
        assertArrayEquals(
            new byte[]{-78, 87, -121, 2, 0, 0, 0, 0},
            new GroupIdentity(identity, 42424242).getGroupIdByteArray()
        );
    }

    @Test
    public void testColorIndexRange() {
        assertValidColorIndex(0);
        assertValidColorIndex(42);
        assertValidColorIndex(254);
        assertValidColorIndex(255);

        assertInvalidColorIndex(Integer.MIN_VALUE);
        assertInvalidColorIndex(-1);
        assertInvalidColorIndex(256);
        assertInvalidColorIndex(Integer.MAX_VALUE);
    }

    private void assertValidColorIndex(int colorIndex) {
        GroupModelData.javaCreate(
            "",
            42,
            "Group",
            new Date(),
            new Date(),
            null,
            false,
            false,
            colorIndex,
            "Description",
            new Date(),
            Collections.emptySet(),
            ch.threema.storage.models.GroupModel.UserState.MEMBER
        );
    }

    private void assertInvalidColorIndex(int colorIndex) {
        assertThrows(
            IllegalArgumentException.class,
            () -> GroupModelData.javaCreate(
                "",
                42,
                "Group",
                new Date(),
                new Date(),
                null,
                false,
                false,
                colorIndex,
                "Description",
                new Date(),
                Collections.emptySet(),
                ch.threema.storage.models.GroupModel.UserState.MEMBER
            )
        );
    }

}
