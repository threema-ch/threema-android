/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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
import android.database.sqlite.SQLiteException;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion10;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion99;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion11;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion12;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion13;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion14;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion15;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion16;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion17;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion19;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion20;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion21;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion24;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion25;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion27;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion28;
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
import ch.threema.app.services.systemupdate.SystemUpdateToVersion65;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion66;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion67;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion68;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion69;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion7;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion70;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion71;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion72;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion73;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion74;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion75;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion76;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion77;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion78;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion79;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion8;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion80;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion81;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion82;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion83;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion84;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion85;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion86;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion87;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion88;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion89;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion9;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion90;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion91;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion92;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion93;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion94;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion95;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion96;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion97;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion98;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion100;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion101;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion102;
import ch.threema.app.services.systemupdate.SystemUpdateToVersion103;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.factories.BallotChoiceModelFactory;
import ch.threema.storage.factories.BallotModelFactory;
import ch.threema.storage.factories.BallotVoteModelFactory;
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.ConversationTagFactory;
import ch.threema.storage.factories.DistributionListMemberModelFactory;
import ch.threema.storage.factories.DistributionListMessageModelFactory;
import ch.threema.storage.factories.DistributionListModelFactory;
import ch.threema.storage.factories.EditHistoryEntryModelFactory;
import ch.threema.storage.factories.GroupBallotModelFactory;
import ch.threema.storage.factories.GroupCallModelFactory;
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.factories.GroupMemberModelFactory;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.GroupModelFactory;
import ch.threema.storage.factories.IncomingGroupSyncRequestLogModelFactory;
import ch.threema.storage.factories.OutgoingGroupSyncRequestLogModelFactory;
import ch.threema.storage.factories.IdentityBallotModelFactory;
import ch.threema.storage.factories.IncomingGroupJoinRequestModelFactory;
import ch.threema.storage.factories.MessageModelFactory;
import ch.threema.storage.factories.ModelFactory;
import ch.threema.storage.factories.OutgoingGroupJoinRequestModelFactory;
import ch.threema.storage.factories.RejectedGroupMessageFactory;
import ch.threema.storage.factories.ServerMessageModelFactory;
import ch.threema.storage.factories.TaskArchiveFactory;
import ch.threema.storage.factories.WebClientSessionModelFactory;

public class DatabaseServiceNew extends SQLiteOpenHelper {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DatabaseServiceNew");

    public static final String DEFAULT_DATABASE_NAME_V4 = "threema4.db";
    public static final String DATABASE_BACKUP_EXT = ".backup";
    private static final int DATABASE_VERSION = SystemUpdateToVersion103.VERSION;

    private final Context context;
    private final UpdateSystemService updateSystemService;

    private ContactModelFactory contactModelFactory;
    private MessageModelFactory messageModelFactory;
    private GroupModelFactory groupModelFactory;
    private GroupMemberModelFactory groupMemberModelFactory;
    private GroupMessageModelFactory groupMessageModelFactory;
    private DistributionListModelFactory distributionListModelFactory;
    private DistributionListMemberModelFactory distributionListMemberModelFactory;
    private DistributionListMessageModelFactory distributionListMessageModelFactory;
    private OutgoingGroupSyncRequestLogModelFactory outgoingGroupSyncRequestLogModelFactory;
    private IncomingGroupSyncRequestLogModelFactory incomingGroupSyncRequestLogModelFactory;
    private BallotModelFactory ballotModelFactory;
    private BallotChoiceModelFactory ballotChoiceModelFactory;
    private BallotVoteModelFactory ballotVoteModelFactory;
    private IdentityBallotModelFactory identityBallotModelFactory;
    private GroupBallotModelFactory groupBallotModelFactory;
    private WebClientSessionModelFactory webClientSessionModelFactory;
    private ConversationTagFactory conversationTagFactory;
    private GroupInviteModelFactory groupInviteModelFactory;
    private OutgoingGroupJoinRequestModelFactory outgoingGroupJoinRequestModelFactory;
    private IncomingGroupJoinRequestModelFactory incomingGroupJoinRequestModelFactory;
    private GroupCallModelFactory groupCallModelFactory;
    private ServerMessageModelFactory serverMessageModelFactory;
    private TaskArchiveFactory taskArchiveFactory;
    private RejectedGroupMessageFactory rejectedGroupMessageFactory;
    private EditHistoryEntryModelFactory contactEditHistoryEntryModelFactory;
    private EditHistoryEntryModelFactory groupEditHistoryEntryModelFactory;

    public DatabaseServiceNew(
        final Context context,
        final @NonNull String databaseKey,
        final @NonNull UpdateSystemService updateSystemService
    ) {
        this(context, DEFAULT_DATABASE_NAME_V4, databaseKey, updateSystemService);
    }

    public DatabaseServiceNew(
        final Context context,
        final @Nullable String databaseName,
        final @NonNull String databaseKey,
        final @NonNull UpdateSystemService updateSystemService
    ) {
        super(
            context,
            databaseName,
            databaseKey,
            null,
            DATABASE_VERSION,
            0,
            sqLiteDatabase -> {
                logger.error("Database corrupted");
                RuntimeUtil.runOnUiThread(() -> {
                    if (context != null) {
                        Toast.makeText(context, "Database corrupted. Please save all data!", Toast.LENGTH_LONG).show();
                    }
                });

                // close database
                if (sqLiteDatabase.isOpen()) {
                    try {
                        sqLiteDatabase.close();
                    } catch (Exception e) {
                        logger.error("Exception while closing database", e);
                    }
                }
                System.exit(2);
            },
            new SQLiteDatabaseHook() {
                @Override
                public void preKey(SQLiteConnection connection) {
                    connection.execute("PRAGMA cipher_default_kdf_iter = 1;", new Object[]{}, null);
                }

                @Override
                public void postKey(SQLiteConnection connection) {
                    connection.execute("PRAGMA kdf_iter = 1;", new Object[]{}, null);
                }
            }
            ,
            true
        );

        logger.info("instantiated");

        this.updateSystemService = updateSystemService;
        this.context = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys = ON;");
        super.onConfigure(db);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        for (ModelFactory f : new ModelFactory[]{
            this.getContactModelFactory(),
            this.getMessageModelFactory(),
            this.getGroupModelFactory(),
            this.getGroupMemberModelFactory(),
            this.getGroupMessageModelFactory(),
            this.getDistributionListModelFactory(),
            this.getDistributionListMemberModelFactory(),
            this.getDistributionListMessageModelFactory(),
            this.getOutgoingGroupSyncRequestLogModelFactory(),
            this.getIncomingGroupSyncRequestLogModelFactory(),
            this.getBallotModelFactory(),
            this.getBallotChoiceModelFactory(),
            this.getBallotVoteModelFactory(),
            this.getIdentityBallotModelFactory(),
            this.getGroupBallotModelFactory(),
            this.getWebClientSessionModelFactory(),
            this.getConversationTagFactory(),
            this.getGroupInviteModelFactory(),
            this.getIncomingGroupJoinRequestModelFactory(),
            this.getOutgoingGroupJoinRequestModelFactory(),
            this.getGroupCallModelFactory(),
            this.getServerMessageModelFactory(),
            this.getTaskArchiveFactory(),
            this.getRejectedGroupMessageFactory(),
            this.getContactEditHistoryEntryModelFactory(),
            this.getGroupEditHistoryEntryModelFactory()
        }) {
            String[] createTableStatement = f.getStatements();
            if (createTableStatement != null) {
                for (String statement : createTableStatement) {
                    if (!TestUtil.isEmptyOrNull(statement)) {
                        sqLiteDatabase.execSQL(statement);
                    }
                }
            }
        }
    }

    @NonNull
    public ContactModelFactory getContactModelFactory() {
        if (this.contactModelFactory == null) {
            this.contactModelFactory = new ContactModelFactory(this);
        }
        return this.contactModelFactory;
    }

    @NonNull
    public MessageModelFactory getMessageModelFactory() {
        if (this.messageModelFactory == null) {
            this.messageModelFactory = new MessageModelFactory(this);
        }
        return this.messageModelFactory;
    }

    @NonNull
    public GroupModelFactory getGroupModelFactory() {
        if (this.groupModelFactory == null) {
            this.groupModelFactory = new GroupModelFactory(this);
        }
        return this.groupModelFactory;
    }

    @NonNull
    public GroupMemberModelFactory getGroupMemberModelFactory() {
        if (this.groupMemberModelFactory == null) {
            this.groupMemberModelFactory = new GroupMemberModelFactory(this);
        }
        return this.groupMemberModelFactory;
    }

    @NonNull
    public GroupMessageModelFactory getGroupMessageModelFactory() {
        if (this.groupMessageModelFactory == null) {
            this.groupMessageModelFactory = new GroupMessageModelFactory(this);
        }
        return this.groupMessageModelFactory;
    }

    @NonNull
    public DistributionListModelFactory getDistributionListModelFactory() {
        if (this.distributionListModelFactory == null) {
            this.distributionListModelFactory = new DistributionListModelFactory(this);
        }
        return this.distributionListModelFactory;
    }

    @NonNull
    public DistributionListMemberModelFactory getDistributionListMemberModelFactory() {
        if (this.distributionListMemberModelFactory == null) {
            this.distributionListMemberModelFactory = new DistributionListMemberModelFactory(this);
        }
        return this.distributionListMemberModelFactory;
    }

    @NonNull
    public DistributionListMessageModelFactory getDistributionListMessageModelFactory() {
        if (this.distributionListMessageModelFactory == null) {
            this.distributionListMessageModelFactory = new DistributionListMessageModelFactory(this);
        }
        return this.distributionListMessageModelFactory;
    }

    @NonNull
    public OutgoingGroupSyncRequestLogModelFactory getOutgoingGroupSyncRequestLogModelFactory() {
        if (this.outgoingGroupSyncRequestLogModelFactory == null) {
            this.outgoingGroupSyncRequestLogModelFactory = new OutgoingGroupSyncRequestLogModelFactory(this);
        }
        return this.outgoingGroupSyncRequestLogModelFactory;
    }

    @NonNull
    public IncomingGroupSyncRequestLogModelFactory getIncomingGroupSyncRequestLogModelFactory() {
        if (this.incomingGroupSyncRequestLogModelFactory == null) {
            this.incomingGroupSyncRequestLogModelFactory = new IncomingGroupSyncRequestLogModelFactory(this);
        }
        return this.incomingGroupSyncRequestLogModelFactory;
    }

    @NonNull
    public BallotModelFactory getBallotModelFactory() {
        if (this.ballotModelFactory == null) {
            this.ballotModelFactory = new BallotModelFactory(this);
        }
        return this.ballotModelFactory;
    }

    @NonNull
    public BallotChoiceModelFactory getBallotChoiceModelFactory() {
        if (this.ballotChoiceModelFactory == null) {
            this.ballotChoiceModelFactory = new BallotChoiceModelFactory(this);
        }
        return this.ballotChoiceModelFactory;
    }

    @NonNull
    public BallotVoteModelFactory getBallotVoteModelFactory() {
        if (this.ballotVoteModelFactory == null) {
            this.ballotVoteModelFactory = new BallotVoteModelFactory(this);
        }
        return this.ballotVoteModelFactory;
    }

    @NonNull
    public IdentityBallotModelFactory getIdentityBallotModelFactory() {
        if (this.identityBallotModelFactory == null) {
            this.identityBallotModelFactory = new IdentityBallotModelFactory(this);
        }
        return this.identityBallotModelFactory;
    }

    @NonNull
    public GroupBallotModelFactory getGroupBallotModelFactory() {
        if (this.groupBallotModelFactory == null) {
            this.groupBallotModelFactory = new GroupBallotModelFactory(this);
        }
        return this.groupBallotModelFactory;
    }

    @NonNull
    public WebClientSessionModelFactory getWebClientSessionModelFactory() {
        if (this.webClientSessionModelFactory == null) {
            this.webClientSessionModelFactory = new WebClientSessionModelFactory(this);
        }
        return this.webClientSessionModelFactory;
    }

    @NonNull
    public ConversationTagFactory getConversationTagFactory() {
        if (this.conversationTagFactory == null) {
            this.conversationTagFactory = new ConversationTagFactory(this);
        }
        return this.conversationTagFactory;
    }

    @NonNull
    public GroupInviteModelFactory getGroupInviteModelFactory() {
        if (this.groupInviteModelFactory == null) {
            this.groupInviteModelFactory = new GroupInviteModelFactory(this);
        }
        return this.groupInviteModelFactory;
    }

    @NonNull
    public IncomingGroupJoinRequestModelFactory getIncomingGroupJoinRequestModelFactory() {
        if (this.incomingGroupJoinRequestModelFactory == null) {
            this.incomingGroupJoinRequestModelFactory = new IncomingGroupJoinRequestModelFactory(this);
        }
        return this.incomingGroupJoinRequestModelFactory;
    }

    @NonNull
    public OutgoingGroupJoinRequestModelFactory getOutgoingGroupJoinRequestModelFactory() {
        if (this.outgoingGroupJoinRequestModelFactory == null) {
            this.outgoingGroupJoinRequestModelFactory = new OutgoingGroupJoinRequestModelFactory(this);
        }
        return this.outgoingGroupJoinRequestModelFactory;
    }

    @NonNull
    public GroupCallModelFactory getGroupCallModelFactory() {
        if (this.groupCallModelFactory == null) {
            this.groupCallModelFactory = new GroupCallModelFactory(this);
        }
        return this.groupCallModelFactory;
    }

    @NonNull
    public ServerMessageModelFactory getServerMessageModelFactory() {
        if (this.serverMessageModelFactory == null) {
            this.serverMessageModelFactory = new ServerMessageModelFactory(this);
        }
        return this.serverMessageModelFactory;
    }

    @NonNull
    public TaskArchiveFactory getTaskArchiveFactory() {
        if (this.taskArchiveFactory == null) {
            this.taskArchiveFactory = new TaskArchiveFactory(this);
        }
        return this.taskArchiveFactory;
    }

    @NonNull
    public RejectedGroupMessageFactory getRejectedGroupMessageFactory() {
        if (this.rejectedGroupMessageFactory == null) {
            this.rejectedGroupMessageFactory = new RejectedGroupMessageFactory(this);
        }
        return this.rejectedGroupMessageFactory;
    }

    @NonNull
    private EditHistoryEntryModelFactory getContactEditHistoryEntryModelFactory() {
        if (this.contactEditHistoryEntryModelFactory == null) {
            this.contactEditHistoryEntryModelFactory = new ContactEditHistoryEntryModelFactory(this);
        }
        return this.contactEditHistoryEntryModelFactory;
    }

    @NonNull
    private EditHistoryEntryModelFactory getGroupEditHistoryEntryModelFactory() {
        if (this.groupEditHistoryEntryModelFactory == null) {
            this.groupEditHistoryEntryModelFactory = new GroupEditHistoryEntryModelFactory(this);
        }
        return this.groupEditHistoryEntryModelFactory;
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

        if (oldVersion < 19) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion19(sqLiteDatabase));
        }

        if (oldVersion < 20) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion20(sqLiteDatabase));
        }

        if (oldVersion < 21) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion21(this, sqLiteDatabase));
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
        if (oldVersion < SystemUpdateToVersion65.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion65(this, sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion66.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion66(this.context));
        }
        if (oldVersion < SystemUpdateToVersion67.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion67(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion68.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion68(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion69.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion69(this, sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion70.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion70(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion71.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion71(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion72.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion72(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion73.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion73(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion74.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion74(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion75.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion75(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion76.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion76(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion77.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion77(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion78.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion78(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion79.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion79(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion80.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion80(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion81.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion81(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion82.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion82(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion83.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion83(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion84.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion84(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion85.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion85(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion86.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion86(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion87.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion87(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion88.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion88(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion89.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion89(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion90.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion90(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion91.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion91(this.context));
        }
        if (oldVersion < SystemUpdateToVersion92.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion92(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion93.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion93(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion94.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion94(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion95.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion95(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion96.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion96(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion97.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion97(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion98.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion98(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion99.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion99(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion100.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion100(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion101.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion101(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion102.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion102(sqLiteDatabase));
        }
        if (oldVersion < SystemUpdateToVersion103.VERSION) {
            this.updateSystemService.addUpdate(new SystemUpdateToVersion103(sqLiteDatabase));
        }
    }

    public void executeNull() throws SQLiteException {
        try {
            getWritableDatabase().rawQuery("SELECT NULL").close();
        } catch (Exception e) {
            logger.error("Unable to execute initial query", e);
        }
    }
}
