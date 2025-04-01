/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.testutils;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.util.Log;

import com.neilalexander.jnacl.NaCl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.UserService;
import ch.threema.base.utils.Utils;
import ch.threema.domain.helpers.InMemoryIdentityStore;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static org.junit.Assert.assertNotNull;

public class TestHelpers {
    private static final String TAG = "TestHelpers";

    public static final TestContact TEST_CONTACT = new TestContact(
        "XERCUKNS",
        Utils.hexStringToByteArray("2bbc16092ff45ffcd0045c00f2f5e1e9597621f89360bbca23a2a2956b3c3b36"),
        Utils.hexStringToByteArray("977aba4ab367041f6137afef69ab9676d445011ca7aca0455a5c64805b80b77a")
    );

    public static final class TestContact {
        @NonNull
        public final String identity;
        @NonNull
        public final byte[] publicKey;
        @NonNull
        public final byte[] privateKey;

        public TestContact(@NonNull String identity) {
            this.identity = identity;
            publicKey = new byte[NaCl.PUBLICKEYBYTES];
            privateKey = new byte[NaCl.SECRETKEYBYTES];

            NaCl.genkeypair(publicKey, privateKey);
        }

        public TestContact(@NonNull String identity, @NonNull byte[] publicKey, @NonNull byte[] privateKey) {
            this.identity = identity;
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        @NonNull
        public Contact getContact() {
            return new Contact(this.identity, this.publicKey, VerificationLevel.UNVERIFIED);
        }

        @NonNull
        public ContactModel getContactModel() {
            return new ContactModel(this.identity, this.publicKey);
        }

        @NonNull
        public IdentityStoreInterface getIdentityStore() {
            return new InMemoryIdentityStore(
                this.identity,
                "",
                this.privateKey,
                null
            );
        }

        @NonNull
        public BasicContact toBasicContact() {
            return BasicContact.javaCreate(
                identity,
                publicKey,
                new ThreemaFeature.Builder()
                    .audio(true)
                    .group(true)
                    .ballot(true)
                    .file(true)
                    .voip(true)
                    .videocalls(true)
                    .forwardSecurity(true)
                    .groupCalls(true)
                    .editMessages(true)
                    .deleteMessages(true)
                    .build(),
                IdentityState.ACTIVE,
                IdentityType.NORMAL
            );
        }
    }

    public static final class TestGroup {
        private int localGroupId = -1;

        @NonNull
        public final GroupId apiGroupId;

        @NonNull
        public final TestContact groupCreator;

        @NonNull
        public final List<TestContact> members;

        @NonNull
        public final String groupName;

        @Nullable
        public final byte[] profilePicture;

        /**
         * Note that the user identity is used to set the correct group user state.
         */
        @NonNull
        public final String userIdentity;

        public TestGroup(
            @NonNull GroupId apiGroupId,
            @NonNull TestContact groupCreator,
            @NonNull List<TestContact> members,
            @NonNull String groupName,
            @NonNull String userIdentity
        ) {
            this(apiGroupId, groupCreator, members, groupName, null, userIdentity);
        }

        public TestGroup(
            @NonNull GroupId apiGroupId,
            @NonNull TestContact groupCreator,
            @NonNull List<TestContact> members,
            @NonNull String groupName,
            @Nullable byte[] profilePicture,
            @NonNull String userIdentity
        ) {
            this.apiGroupId = apiGroupId;
            this.groupCreator = groupCreator;
            this.members = members;
            this.groupName = groupName;
            this.profilePicture = profilePicture;
            this.userIdentity = userIdentity;
        }

        @NonNull
        public GroupModel getGroupModel() {
            boolean isMember = false;
            for (TestContact member : members) {
                if (member.identity.equals(userIdentity)) {
                    isMember = true;
                    break;
                }
            }
            return getGroupModel(isMember ? GroupModel.UserState.MEMBER : GroupModel.UserState.LEFT);
        }

        @NonNull
        private GroupModel getGroupModel(@NonNull GroupModel.UserState userState) {
            return new GroupModel()
                .setApiGroupId(apiGroupId)
                .setCreatedAt(new Date())
                .setName(this.groupName)
                .setCreatorIdentity(this.groupCreator.identity)
                .setId(localGroupId)
                .setUserState(userState);
        }

        public void setLocalGroupId(int localGroupId) {
            this.localGroupId = localGroupId;
        }
    }

    /**
     * Open the notification area and wait for the notifications to become visible.
     *
     * @param device UiDevice instance
     */
    public static void openNotificationArea(@NonNull UiDevice device) throws AssertionError {
        device.openNotification();

        // Wait for notifications to appear
        final BySelector selector = By.res("android:id/status_bar_latest_event_content");
        assertNotNull(
            "Notification bar latest event content not found",
            device.wait(Until.findObject(selector), 1000)
        );
    }

    /**
     * Source: https://stackoverflow.com/a/5921190/284318
     */
    public static boolean iServiceRunning(@NonNull Context appContext, @NonNull Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensure that an identity is set up.
     */
    public static String ensureIdentity(@NonNull ServiceManager serviceManager) throws Exception {
        // Check whether identity already exists
        final UserService userService = serviceManager.getUserService();
        if (userService.hasIdentity()) {
            final String identity = userService.getIdentity();
            Log.i(TAG, "Identity already exists: " + identity);
            return identity;
        }

        // Otherwise, create identity
        userService.restoreIdentity(
            TEST_CONTACT.identity,
            TEST_CONTACT.privateKey,
            TEST_CONTACT.publicKey
        );
        Log.i(TAG, "Test identity restored: " + TEST_CONTACT.identity);
        return TEST_CONTACT.identity;
    }

    public static void clearLogcat() {
        try {
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"});
        } catch (IOException e) {
            Log.e(TAG, "Could not clear logcat", e);
        }
    }

    /**
     * Return adb logs since the start of the specified test.
     * <p>
     * Based on https://www.braze.com/resources/articles/logcat-junit-android-tests
     */
    public static String getTestLogs(@NonNull String testName) {
        final StringBuilder logLines = new StringBuilder();

        // Process id is used to filter messages
        final String currentProcessId = Integer.toString(android.os.Process.myPid());

        // A snippet of text that uniquely determines where the relevant logs start in the logcat
        final String testStartMessage = "TestRunner: started: " + testName;

        // When true, write every line from the logcat buffer to the string builder
        boolean recording = false;

        // Logcat command:
        //   -d asks the command to completely dump to our buffer, then return
        //   -v threadtime sets the output log format
        final String[] command = new String[]{"logcat", "-d", "-v", "threadtime"};

        BufferedReader bufferedReader = null;
        try {
            final Process process = Runtime.getRuntime().exec(command);
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(testStartMessage)) {
                    recording = true;
                }
                if (recording) {
                    logLines.append(line);
                    logLines.append('\n');
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to run logcat command", e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close buffered reader", e);
                }
            }
        }

        return logLines.toString();
    }
}
