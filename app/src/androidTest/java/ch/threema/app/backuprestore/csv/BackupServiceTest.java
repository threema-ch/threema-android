/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

package ch.threema.app.backuprestore.csv;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.AbstractFileHeader;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;
import ch.threema.app.DangerousTest;
import ch.threema.app.TestHelpers;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.BackupRestoreDataConfig;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.utils.CSVReader;
import ch.threema.app.utils.CSVRow;
import ch.threema.client.IdentityBackupDecoder;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;
import java8.util.stream.StreamSupport;

@RunWith(AndroidJUnit4.class)
@LargeTest
@DangerousTest // Deletes data and possibly identity
public class BackupServiceTest {
	private final static String PASSWORD = "ubnpwrgujioasdfi0932";
	private static final String TAG = "BackupServiceTest";

	@SuppressWarnings("NotNullFieldNotInitialized")
	private static @NonNull String TEST_IDENTITY;

	// Services
	private @NonNull FileService fileService;
    private @NonNull MessageService messageService;
    private @NonNull ConversationService conversationService;
    private @NonNull GroupService groupService;
    private @NonNull ContactService contactService;
    private @NonNull DistributionListService distributionListService;
    private @NonNull BallotService ballotService;

	@Rule
	public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

	/**
	 * Ensure that an identity is set up, initialize static {@link #TEST_IDENTITY} variable.
	 */
	@BeforeClass
	public static void ensureIdentityExists() throws Exception {
		// Set up identity
		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		TEST_IDENTITY = TestHelpers.ensureIdentity(Objects.requireNonNull(serviceManager));
	}

	/**
	 * Load Threema services.
	 */
	@Before
	public void loadServices() throws Exception {
		final ServiceManager serviceManager = Objects.requireNonNull(ThreemaApplication.getServiceManager());
		this.fileService = serviceManager.getFileService();
		this.messageService = serviceManager.getMessageService();
		this.conversationService = serviceManager.getConversationService();
		this.groupService = serviceManager.getGroupService();
		this.contactService = serviceManager.getContactService();
		this.distributionListService = serviceManager.getDistributionListService();
		this.ballotService = serviceManager.getBallotService();
	}

	/**
	 * Return the list of backups for the TEST_IDENTITY identity.
	 */
	private @NonNull List<File> getUserBackups(@NonNull File backupPath) throws FileSystemNotPresentException {
		if (backupPath.exists() && backupPath.isDirectory()) {
			final File[] files = backupPath.listFiles(
				(dir, name) -> name.startsWith("threema-backup_" + TEST_IDENTITY)
			);
			return files == null ? new ArrayList<>() : Arrays.asList(files);
		} else {
			return new ArrayList<>();
		}
	}

	/**
	 * Helper method: Create a backup with the specified config, return backup file.
	 */
	private @NonNull File doBackup(BackupRestoreDataConfig config) throws Exception {
		// List old backups
		final File backupPath = this.fileService.getBackupPath();
		final List<File> initialBackupFiles = this.getUserBackups(backupPath);


		// Prepare service intent
		final Context appContext = ApplicationProvider.getApplicationContext();
		final Intent intent = new Intent(appContext, BackupService.class);
		intent.putExtra(BackupService.EXTRA_BACKUP_RESTORE_DATA_CONFIG, config);

		// Start service
		ContextCompat.startForegroundService(appContext, intent);
		Assert.assertTrue(TestHelpers.iServiceRunning(appContext, BackupService.class));

		// Wait for service to stop
		while (TestHelpers.iServiceRunning(appContext, BackupService.class)) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// ignore
			}
		}

		// Check that a backup file has been created
		Assert.assertTrue(backupPath.exists());
		Assert.assertTrue(backupPath.isDirectory());
		File backupFile = null;
		for (File file : getUserBackups(backupPath)) {
			if (!initialBackupFiles.contains(file)) {
				if (backupFile != null) {
					Assert.fail("Found more than one new backup: " + backupFile + " and " + file);
				}
				backupFile = file;
			}
		}
		Assert.assertNotNull("New backup file not found", backupFile);
		Assert.assertTrue(backupFile.exists());
		Assert.assertTrue(backupFile.isFile());

		return backupFile;
	}

	/**
	 * Unpack the backup from the specified backup file and ensure
	 * that the specified files are contained.
	 */
	private ZipFile openBackupFile(
		@NonNull File backupFile,
		@NonNull String[] expectedFiles
	) throws Exception {
		// Open ZIP
		final ZipFile zipFile = new ZipFile(backupFile, PASSWORD.toCharArray());
		Assert.assertTrue("Generated backup ZIP is invalid", zipFile.isValidZipFile());

		// Ensure list of files is correct
		final List<FileHeader> headers = zipFile.getFileHeaders();
		Log.d(TAG, "File headers: " + Arrays.toString(headers.toArray()));
		final Object[] actualFiles = StreamSupport.stream(headers)
			.map(AbstractFileHeader::getFileName)
			.toArray();
		Assert.assertArrayEquals(
			"Array is " + Arrays.toString(actualFiles),
			expectedFiles,
			actualFiles
		);

		return zipFile;
	}

	@Test
	public void testBackupIdentity() throws Exception {
		// Do backup
		final File backupFile = doBackup(new BackupRestoreDataConfig(PASSWORD)
			.setBackupContactAndMessages(false)
			.setBackupIdentity(true)
			.setBackupAvatars(false)
			.setBackupMedia(false)
			.setBackupThumbnails(false)
			.setBackupVideoAndFiles(false));

		try {
			final ZipFile zipFile = this.openBackupFile(backupFile, new String[]{ "settings", "identity" });

			// Read identity backup
			final String identityBackup;
			try (final ZipInputStream stream = zipFile.getInputStream(zipFile.getFileHeader("identity"))) {
				identityBackup = IOUtils.toString(stream);
			}

			// Verify identity backup
			final IdentityBackupDecoder identityBackupDecoder = new IdentityBackupDecoder(identityBackup);
			Assert.assertTrue("Could not decode identity backup", identityBackupDecoder.decode(PASSWORD));
			Assert.assertEquals(TEST_IDENTITY, identityBackupDecoder.getIdentity());
		} finally {
			//noinspection ResultOfMethodCallIgnored
			backupFile.delete();
		}
	}

    @Test
    public void testBackupContactsAndMessages() throws Exception {
        // Clear all data
        this.messageService.removeAll();
        this.conversationService.reset();
        this.groupService.removeAll();
        this.contactService.removeAll();
        this.distributionListService.removeAll();
        this.ballotService.removeAll();

        // Insert test data:
	    // Contacts
	    final ContactModel contact1 = this.contactService.createContactByIdentity("CDXVZ5E4", true);
	    contact1.setFirstName("Fritzli");
	    contact1.setLastName("Bühler");
	    this.contactService.save(contact1);
	    final ContactModel contact2 = this.contactService.createContactByIdentity("DRMWZP3H", true);
	    this.contactService.createContactByIdentity("ECHOECHO", true);
	    // Messages contact 1
	    this.messageService.sendText("Bonjour!", this.contactService.createReceiver(contact1));
	    this.messageService.sendText("Phở?", this.contactService.createReceiver(contact1));
	    this.messageService.createVoipStatus(VoipStatusDataModel.createAborted(), this.contactService.createReceiver(contact1), true, false);
	    // Messages contact 2
	    this.messageService.sendText("\uD83D\uDC4B", this.contactService.createReceiver(contact2));

        // Do backup
        final File backupFile = doBackup(new BackupRestoreDataConfig(PASSWORD)
            .setBackupContactAndMessages(true)
            .setBackupIdentity(false)
            .setBackupAvatars(false)
            .setBackupMedia(false)
            .setBackupThumbnails(false)
            .setBackupVideoAndFiles(false));

        try {
            final ZipFile zipFile = this.openBackupFile(backupFile, new String[]{
                "settings",
                "message_CDXVZ5E4.csv",
                "message_DRMWZP3H.csv",
                "message_ECHOECHO.csv",
                "contacts.csv",
                "groups.csv",
                "distribution_list.csv",
                "ballot.csv",
                "ballot_choice.csv",
                "ballot_vote.csv",
            });

            // Read contacts
            try (final ZipInputStream stream = zipFile.getInputStream(zipFile.getFileHeader("contacts.csv"))) {
	            final CSVReader csvReader = new CSVReader(new InputStreamReader(stream), true);
	            final CSVRow row1 = csvReader.readNextRow();
	            Assert.assertEquals("CDXVZ5E4", row1.getString("identity"));
	            Assert.assertEquals("Fritzli", row1.getString("firstname"));
	            Assert.assertEquals("Bühler", row1.getString("lastname"));
	            final CSVRow row2 = csvReader.readNextRow();
	            Assert.assertEquals("DRMWZP3H", row2.getString("identity"));
	            final CSVRow row3 = csvReader.readNextRow();
	            Assert.assertEquals("ECHOECHO", row3.getString("identity"));
            }

	        // Read messages
	        try (final ZipInputStream stream = zipFile.getInputStream(zipFile.getFileHeader("message_CDXVZ5E4.csv"))) {
		        final CSVReader csvReader = new CSVReader(new InputStreamReader(stream), true);
		        // First, the two text messages
		        final CSVRow row1 = csvReader.readNextRow();
		        final CSVRow row2 = csvReader.readNextRow();
		        Assert.assertTrue(row1.getBoolean("isoutbox"));
		        Assert.assertTrue(row2.getBoolean("isoutbox"));
		        Assert.assertEquals("TEXT", row1.getString("type"));
		        Assert.assertEquals("TEXT", row2.getString("type"));
		        Assert.assertEquals("Bonjour!", row1.getString("body"));
		        Assert.assertEquals("Phở?", row2.getString("body"));
		        // …followed by the VoIPstatus message
		        final CSVRow row3 = csvReader.readNextRow();
		        Assert.assertEquals("VOIP_STATUS", row3.getString("type"));
		        Assert.assertEquals("[1,{\"status\":" + VoipStatusDataModel.ABORTED + "}]", row3.getString("body"));
		        Assert.assertNull(csvReader.readNextRow());
	        }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            backupFile.delete();
        }
    }


}
