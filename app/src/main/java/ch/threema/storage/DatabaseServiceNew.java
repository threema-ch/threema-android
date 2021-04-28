/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.storage;

import android.content.Context;
import android.text.format.DateUtils;
import android.widget.Toast;

import net.sqlcipher.DatabaseErrorHandler;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import androidx.annotation.MainThread;
import ch.threema.app.exceptions.DatabaseMigrationFailedException;
import ch.threema.app.exceptions.DatabaseMigrationLockedException;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion10;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion11;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion12;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion13;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion14;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion15;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion16;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion17;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion18;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion19;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion20;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion21;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion23;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion24;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion25;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion27;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion28;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion30;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion31;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion32;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion33;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion34;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion35;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion36;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion37;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion38;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion39;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion4;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion40;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion41;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion42;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion43;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion44;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion45;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion46;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion47;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion48;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion49;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion50;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion51;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion52;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion53;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion54;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion55;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion56;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion58;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion59;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion6;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion60;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion61;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion62;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion63;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion64;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion7;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion8;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion9;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.factories.BallotChoiceModelFactory;
import ch.threema.storage.factories.BallotModelFactory;
import ch.threema.storage.factories.BallotVoteModelFactory;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.ConversationTagFactory;
import ch.threema.storage.factories.DistributionListMemberModelFactory;
import ch.threema.storage.factories.DistributionListMessageModelFactory;
import ch.threema.storage.factories.DistributionListModelFactory;
import ch.threema.storage.factories.GroupBallotModelFactory;
import ch.threema.storage.factories.GroupMemberModelFactory;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.GroupMessagePendingMessageIdModelFactory;
import ch.threema.storage.factories.GroupModelFactory;
import ch.threema.storage.factories.GroupRequestSyncLogModelFactory;
import ch.threema.storage.factories.IdentityBallotModelFactory;
import ch.threema.storage.factories.MessageModelFactory;
import ch.threema.storage.factories.ModelFactory;
import ch.threema.storage.factories.WebClientSessionModelFactory;

public class DatabaseServiceNew extends SQLiteOpenHelper {
	private static final Logger logger = LoggerFactory.getLogger(DatabaseServiceNew.class);

	public static final String DATABASE_NAME = "threema.db";
	public static final String DATABASE_NAME_V4 = "threema4.db";
	public static final String DATABASE_BACKUP_EXT = ".backup";
	private static final int DATABASE_VERSION = 64;
	private final Context context;
	private final String key;
	private final UpdateSystemService updateSystemService;

	private ContactModelFactory contactModelFactory;
	private MessageModelFactory messageModelFactory;
	private GroupModelFactory groupModelFactory;
	private GroupMemberModelFactory groupMemberModelFactory;
	private GroupMessageModelFactory groupMessageModelFactory;
	private DistributionListModelFactory distributionListModelFactory;
	private DistributionListMemberModelFactory distributionListMemberModelFactory;
	private DistributionListMessageModelFactory distributionListMessageModelFactory;
	private GroupRequestSyncLogModelFactory groupRequestSyncLogModelFactory;
	private BallotModelFactory ballotModelFactory;
	private BallotChoiceModelFactory ballotChoiceModelFactory;
	private BallotVoteModelFactory ballotVoteModelFactory;
	private IdentityBallotModelFactory identityBallotModelFactory;
	private GroupBallotModelFactory groupBallotModelFactory;
	private GroupMessagePendingMessageIdModelFactory groupMessagePendingMessageIdModelFactory;
	private WebClientSessionModelFactory webClientSessionModelFactory;
	private ConversationTagFactory conversationTagFactory;

	public DatabaseServiceNew(final Context context,
	                          final String databaseKey,
	                          UpdateSystemService updateSystemService,
	                          int sqlcipherVersion) {
		super(
				context,
				sqlcipherVersion == 4 ? DATABASE_NAME_V4 : DATABASE_NAME,
				null,
				DATABASE_VERSION,
				new SQLiteDatabaseHook() {
					@Override
					public void preKey(SQLiteDatabase sqLiteDatabase) {
						if (sqlcipherVersion == 4) {
							sqLiteDatabase.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
						} else {
							sqLiteDatabase.rawExecSQL(
								"PRAGMA cipher_default_page_size = 1024;" +
								"PRAGMA cipher_default_kdf_iter = 4000;" +
								"PRAGMA cipher_default_hmac_algorithm = HMAC_SHA1;" +
								"PRAGMA cipher_default_kdf_algorithm = PBKDF2_HMAC_SHA1;");
						}
					}

					@Override
					public void postKey(SQLiteDatabase sqLiteDatabase) {
						if (sqlcipherVersion == 4) {
							sqLiteDatabase.rawExecSQL("PRAGMA kdf_iter = 1;");
							// turn off memory wiping for now due to https://github.com/sqlcipher/android-database-sqlcipher/issues/411
							sqLiteDatabase.rawExecSQL("PRAGMA cipher_memory_security = OFF;");
						} else {
							sqLiteDatabase.rawExecSQL(
								"PRAGMA cipher_page_size = 1024;" +
								"PRAGMA kdf_iter = 4000;" +
								"PRAGMA cipher_hmac_algorithm = HMAC_SHA1;" +
								"PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;");
						}
					}
				}
				,
				new DatabaseErrorHandler() {
					@Override
					public void onCorruption(SQLiteDatabase sqLiteDatabase) {
						logger.error("Database corrupted");
						RuntimeUtil.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if (context != null) {
									Toast.makeText(context, "Database corrupted. Please save all data!", Toast.LENGTH_LONG).show();
								}
							}
						});

						// close database
						if (sqLiteDatabase.isOpen()) {
							try {
								sqLiteDatabase.close();
							} catch (Exception e) {
								//
							}
						}
						System.exit(2);
					}
				}
		);

		logger.info("instantiated");

		this.updateSystemService = updateSystemService;
		this.context = context;

		SQLiteDatabase.loadLibs(context);

		this.key = databaseKey;
	}

	public synchronized SQLiteDatabase getWritableDatabase() throws SQLiteException {
		return super.getWritableDatabase(this.key);
	}
	public synchronized SQLiteDatabase getReadableDatabase()  {
		return super.getReadableDatabase(this.key);
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase) {
		for(ModelFactory f: new ModelFactory[] {this.getContactModelFactory(),
		this.getMessageModelFactory(),
		this.getGroupModelFactory(),
		this.getGroupMemberModelFactory(),
		this.getGroupMessageModelFactory(),
		this.getDistributionListModelFactory(),
		this.getDistributionListMemberModelFactory(),
		this.getDistributionListMessageModelFactory(),
		this.getGroupRequestSyncLogModelFactory(),
		this.getBallotModelFactory(),
		this.getBallotChoiceModelFactory(),
		this.getBallotVoteModelFactory(),
		this.getIdentityBallotModelFactory(),
		this.getGroupBallotModelFactory(),
		this.getGroupMessagePendingMessageIdModelFactory(),
		this.getWebClientSessionModelFactory(),
		this.getConversationTagFactory()})
		{
			String[] createTableStatement = f.getStatements();
			if(createTableStatement != null) {
				for (String statement : createTableStatement) {
					if (!TestUtil.empty(statement)) {
						sqLiteDatabase.execSQL(statement);
					}
				}
			}
		}
	}

	public ContactModelFactory getContactModelFactory() {
		if(this.contactModelFactory == null) {
			this.contactModelFactory = new ContactModelFactory(this);
		}
		return this.contactModelFactory;
	}

	public MessageModelFactory getMessageModelFactory() {
		if(this.messageModelFactory == null) {
			this.messageModelFactory = new MessageModelFactory(this);
		}
		return this.messageModelFactory;
	}

	public GroupModelFactory getGroupModelFactory() {
		if(this.groupModelFactory == null) {
			this.groupModelFactory = new GroupModelFactory(this);
		}
		return this.groupModelFactory;
	}

	public GroupMemberModelFactory getGroupMemberModelFactory() {
		if(this.groupMemberModelFactory == null) {
			this.groupMemberModelFactory = new GroupMemberModelFactory(this);
		}
		return this.groupMemberModelFactory;
	}

	public GroupMessageModelFactory getGroupMessageModelFactory() {
		if(this.groupMessageModelFactory == null) {
			this.groupMessageModelFactory = new GroupMessageModelFactory(this);
		}
		return this.groupMessageModelFactory;
	}

	public DistributionListModelFactory getDistributionListModelFactory() {
		if(this.distributionListModelFactory == null) {
			this.distributionListModelFactory = new DistributionListModelFactory(this);
		}
		return this.distributionListModelFactory;
	}

	public DistributionListMemberModelFactory getDistributionListMemberModelFactory() {
		if(this.distributionListMemberModelFactory == null) {
			this.distributionListMemberModelFactory = new DistributionListMemberModelFactory(this);
		}
		return this.distributionListMemberModelFactory;
	}

	public DistributionListMessageModelFactory getDistributionListMessageModelFactory() {
		if(this.distributionListMessageModelFactory == null) {
			this.distributionListMessageModelFactory = new DistributionListMessageModelFactory(this);
		}
		return this.distributionListMessageModelFactory;
	}

	public GroupRequestSyncLogModelFactory getGroupRequestSyncLogModelFactory() {
		if(this.groupRequestSyncLogModelFactory == null) {
			this.groupRequestSyncLogModelFactory = new GroupRequestSyncLogModelFactory(this);
		}
		return this.groupRequestSyncLogModelFactory;
	}

	public BallotModelFactory getBallotModelFactory() {
		if(this.ballotModelFactory == null) {
			this.ballotModelFactory = new BallotModelFactory(this);
		}
		return this.ballotModelFactory;
	}

	public BallotChoiceModelFactory getBallotChoiceModelFactory() {
		if (this.ballotChoiceModelFactory == null) {
			this.ballotChoiceModelFactory = new BallotChoiceModelFactory(this);
		}
		return this.ballotChoiceModelFactory;
	}


	public BallotVoteModelFactory getBallotVoteModelFactory() {
		if(this.ballotVoteModelFactory == null) {
			this.ballotVoteModelFactory = new BallotVoteModelFactory(this);
		}
		return this.ballotVoteModelFactory;
	}


	public IdentityBallotModelFactory getIdentityBallotModelFactory() {
		if(this.identityBallotModelFactory == null) {
			this.identityBallotModelFactory = new IdentityBallotModelFactory(this);
		}
		return this.identityBallotModelFactory;
	}

	public GroupBallotModelFactory getGroupBallotModelFactory() {
		if(this.groupBallotModelFactory == null) {
			this.groupBallotModelFactory = new GroupBallotModelFactory(this);
		}
		return this.groupBallotModelFactory;
	}

	public GroupMessagePendingMessageIdModelFactory getGroupMessagePendingMessageIdModelFactory() {
		if(this.groupMessagePendingMessageIdModelFactory == null) {
			this.groupMessagePendingMessageIdModelFactory = new GroupMessagePendingMessageIdModelFactory(this);
		}
		return this.groupMessagePendingMessageIdModelFactory;
	}

	public WebClientSessionModelFactory getWebClientSessionModelFactory() {
		if(this.webClientSessionModelFactory == null) {
			this.webClientSessionModelFactory = new WebClientSessionModelFactory(this);
		}
		return this.webClientSessionModelFactory;
	}

	public ConversationTagFactory getConversationTagFactory() {
		if(this.conversationTagFactory == null) {
			this.conversationTagFactory = new ConversationTagFactory(this);
		}
		return this.conversationTagFactory;
	}

	// Note: Enable this to allow database downgrades.
	//
	//@Override
	//public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	//  logger.info("onDowngrade, version {} -> {}", oldVersion, newVersion);
	//}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
		logger.info("onUpgrade, version {} -> {}", oldVersion, newVersion);

		if (oldVersion < 4) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion4(sqLiteDatabase));
		}

		if (oldVersion < 6) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion6(this.context, sqLiteDatabase));
		}

		if (oldVersion < 7) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion7(sqLiteDatabase));
		}

		if (oldVersion < 8) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion8(this, sqLiteDatabase));
		}

		if (oldVersion < 9) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion9(sqLiteDatabase));
		}

		if (oldVersion < 10) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion10(sqLiteDatabase));
		}

		if (oldVersion < 11) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion11(sqLiteDatabase));
		}

		if (oldVersion < 12) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion12(this.context, sqLiteDatabase));
		}

		if (oldVersion < 13) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion13(sqLiteDatabase));
		}

		if (oldVersion < 14) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion14());
		}

		if (oldVersion < 15) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion15(this, sqLiteDatabase));
		}

		if (oldVersion < 16) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion16(sqLiteDatabase));
		}

		if (oldVersion < 17) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion17(sqLiteDatabase));
		}

		if (oldVersion < 18) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion18(sqLiteDatabase));
		}

		if (oldVersion < 19) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion19(sqLiteDatabase));
		}

		if (oldVersion < 20) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion20(sqLiteDatabase));
		}

		if (oldVersion < 21) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion21(this, sqLiteDatabase));
		}

		if (oldVersion < 23) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion23(sqLiteDatabase));
		}

		if (oldVersion < 24) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion24(sqLiteDatabase));
		}

		if (oldVersion < 25) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion25(this, sqLiteDatabase));
		}

		if (oldVersion < 27) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion27(sqLiteDatabase));
		}

		if (oldVersion < 28) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion28(sqLiteDatabase));
		}
		if (oldVersion < 30) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion30(sqLiteDatabase));
		}
		if (oldVersion < 31) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion31(this.context));
		}
		if (oldVersion < 32) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion32(sqLiteDatabase));
		}

		if (oldVersion < 33) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion33(this, sqLiteDatabase));
		}
		if (oldVersion < 34) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion34(sqLiteDatabase));
		}
		if (oldVersion < 35) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion35(sqLiteDatabase));
		}
		if (oldVersion < 36) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion36(sqLiteDatabase));
		}
		if (oldVersion < 37) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion37(this, sqLiteDatabase));
		}
		if (oldVersion < 38) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion38(this, sqLiteDatabase));
		}
		if (oldVersion < 39) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion39());
		}
		if (oldVersion < 40) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion40(sqLiteDatabase));
		}
		if (oldVersion < 41) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion41(sqLiteDatabase));
		}
		if (oldVersion < 42) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion42(sqLiteDatabase));
		}
		if (oldVersion < 43) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion43(sqLiteDatabase));
		}
		if (oldVersion < 44) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion44(sqLiteDatabase));
		}
		if (oldVersion < 45) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion45(this, sqLiteDatabase));
		}
		if (oldVersion < 46) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion46());
		}
		if (oldVersion < 47) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion47(sqLiteDatabase));
		}
		if (oldVersion < 48) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion48(this.context));
		}
		if (oldVersion < 49) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion49(sqLiteDatabase));
		}
		if (oldVersion < 50) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion50(sqLiteDatabase));
		}
		if (oldVersion < 51) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion51(sqLiteDatabase));
		}
		if (oldVersion < 52) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion52(sqLiteDatabase));
		}
		if (oldVersion < 53) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion53());
		}
		if (oldVersion < 54) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion54(this.context));
		}
		if (oldVersion < 55) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion55());
		}
		if (oldVersion < 56) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion56(sqLiteDatabase));
		}
		if (oldVersion < 58) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion58(sqLiteDatabase));
		}
		if (oldVersion < 59) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion59(sqLiteDatabase));
		}
		if (oldVersion < 60) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion60(sqLiteDatabase));
		}
		if (oldVersion < 61) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion61(sqLiteDatabase));
		}
		if (oldVersion < 62) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion62(sqLiteDatabase));
		}
		if (oldVersion < 63) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion63(this.context));
		}
		if (oldVersion < 64) {
			this.updateSystemService.addUpdate(new SystemUpdateToVersion64(this.context));
		}
	}

	public void executeNull() throws SQLiteException {
		this.getWritableDatabase().rawExecSQL("SELECT NULL");
	}

	@MainThread
	public static synchronized void tryMigrateToV4(Context context, final String databaseKey) throws DatabaseMigrationFailedException, DatabaseMigrationLockedException  {
		File oldDatabaseFile = context.getDatabasePath(DATABASE_NAME);
		File newDatabaseFile = context.getDatabasePath(DATABASE_NAME_V4);
		final boolean[] migrateSuccess = {false};

		logger.info("check if v4 database migration is necessary");

		if (oldDatabaseFile.exists()) {
			File lockfile = new File(context.getFilesDir(), ".dbv4-lock");
			if (lockfile.exists()) {
				long lastModified = lockfile.lastModified();
				long now = System.currentTimeMillis();

				if ((now - lastModified) > (5 * DateUtils.MINUTE_IN_MILLIS)) {
					FileUtil.deleteFileOrWarn(lockfile, "Lockfile", logger);
					if (newDatabaseFile.exists()) {
						FileUtil.deleteFileOrWarn(newDatabaseFile, "New Database File", logger);
					}
				} else {
					logger.info("Lockfile exists...exiting");
					throw new DatabaseMigrationLockedException();
				}
			}

			try {
				FileUtil.createNewFileOrLog(lockfile, logger);
			} catch (IOException e) {
				logger.error("Exception", e);
			}

			if (!newDatabaseFile.exists()) {
				logger.info("Database migration to v4 required");

				long usableSpace = oldDatabaseFile.getUsableSpace();
				long fileSize = oldDatabaseFile.length();

				if (usableSpace < (fileSize * 2)) {
					FileUtil.deleteFileOrWarn(lockfile, "Lockfile", logger);
					throw new DatabaseMigrationFailedException("Not enough space left on device");
				}

				Thread migrateThread = new Thread(new Runnable() {
					@Override
					public void run() {

						try {
							// migrate
							SQLiteDatabase.loadLibs(context);
							SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
								@Override
								public void preKey(SQLiteDatabase sqLiteDatabase) {}

								@Override
								public void postKey(SQLiteDatabase sqLiteDatabase) {
									// old settings
									sqLiteDatabase.rawExecSQL(
											"PRAGMA cipher_page_size = 1024;" +
													"PRAGMA kdf_iter = 4000;" +
													"PRAGMA cipher_hmac_algorithm = HMAC_SHA1;" +
													"PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;");
								}
							};

							final int databaseVersion;
							try (SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(oldDatabaseFile.getAbsolutePath(), databaseKey, null, hook)) {
								if (database.isOpen()) {
									databaseVersion = database.getVersion();
									logger.info("Original database version: {}", databaseVersion);

									database.rawExecSQL(
										"PRAGMA key = '" + databaseKey + "';" +
											"PRAGMA cipher_page_size = 1024;" +
											"PRAGMA kdf_iter = 4000;" +
											"PRAGMA cipher_hmac_algorithm = HMAC_SHA1;" +
											"PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;" +
											"ATTACH DATABASE '" + newDatabaseFile.getAbsolutePath() + "' AS threema4 KEY '" + databaseKey + "';" +
											"PRAGMA threema4.kdf_iter = 1;" +
											"PRAGMA threema4.cipher_memory_security = OFF;" +
											"SELECT sqlcipher_export('threema4');" +
											"PRAGMA threema4.user_version = " + databaseVersion + ";" +
											"DETACH DATABASE threema4;");
									database.close();

									logger.info("Database successfully migrated");

									if (checkNewDatabase(newDatabaseFile, databaseKey, databaseVersion)) {
										migrateSuccess[0] = true;
									}
								}
							}
						} catch (Exception e) {
							logger.info("Database migration FAILED");
							logger.error("Exception", e);
							FileUtil.deleteFileOrWarn(newDatabaseFile, "New Database File", logger);
						}
					}
				});

				migrateThread.start();
				try {
					migrateThread.join();
				} catch (InterruptedException e) {
					logger.error("Exception", e);
					migrateSuccess[0] = false;
				}

				if (migrateSuccess[0]) {
					Toast.makeText(context, "Database successfully migrated", Toast.LENGTH_LONG).show();
					logger.info("Migration finished");
				} else {
					logger.info("Migration failed");
					FileUtil.deleteFileOrWarn(newDatabaseFile, "New Database File", logger);
					FileUtil.deleteFileOrWarn(lockfile, "New Database File", logger);
					throw new DatabaseMigrationFailedException();
				}
			} else {
				try {
					SQLiteDatabase.loadLibs(context);

					if (checkNewDatabase(newDatabaseFile, databaseKey, DATABASE_VERSION)) {
						logger.info("Delete old format database");
						FileUtil.deleteFileOrWarn(oldDatabaseFile, "Old Database File", logger);
					} else {
						throw new Exception();
					}
				} catch (Exception e) {
					logger.info("Database checking FAILED");
					FileUtil.deleteFileOrWarn(newDatabaseFile, "New Database File", logger);
					FileUtil.deleteFileOrWarn(lockfile, "Lockfile", logger);
					throw new DatabaseMigrationFailedException();
				}
			}
			FileUtil.deleteFileOrWarn(lockfile, "Lockfile", logger);
		} else {
			logger.info("No old database file found. No migration necessary");
			logger.info("New database file exists = {}", newDatabaseFile.exists());
		}
	}

	private static boolean checkNewDatabase(File newDatabaseFile, String databaseKey, int databaseVersion) {
		// test new database

		try (SQLiteDatabase newDatabase = SQLiteDatabase.openDatabase(newDatabaseFile.getAbsolutePath(), databaseKey, null, 0, new SQLiteDatabaseHook() {
			@Override
			public void preKey(SQLiteDatabase sqLiteDatabase) {
				sqLiteDatabase.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
			}

			@Override
			public void postKey(SQLiteDatabase sqLiteDatabase) {
				sqLiteDatabase.rawExecSQL(
					"PRAGMA kdf_iter = 1;" +
						"PRAGMA cipher_memory_security = OFF;");
			}
		})) {
			if (newDatabase.isOpen()) {
				if (newDatabase.getVersion() == databaseVersion) {
					newDatabase.rawExecSQL("SELECT NULL;");
					logger.info("New database successfully checked. Version set to {}", databaseVersion);
					return true;
				} else {
					logger.info("Database version mismatch. old = {} new = {}", databaseVersion, newDatabase.getVersion());
				}
			} else {
				logger.info("Could not open new database");
			}
		}
		return false;
	}
}
