package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

public class DatabaseUpdateToVersion25 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion25(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String statement : BallotModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
        for (String statement : BallotChoiceModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
        for (String statement : BallotVoteModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
        for (String statement : IdentityBallotModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
        for (String statement : GroupBallotModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
    }

    @Override
    public int getVersion() {
        return 25;
    }

    private static class BallotModel {
        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiBallotId` VARCHAR NOT NULL , `creatorIdentity` VARCHAR NOT NULL , `name` VARCHAR , `state` VARCHAR NOT NULL , `assessment` VARCHAR NOT NULL , `type` VARCHAR NOT NULL , `choiceType` VARCHAR NOT NULL , `displayType` VARCHAR , `createdAt` BIGINT NOT NULL , `modifiedAt` BIGINT NOT NULL , `lastViewedAt` BIGINT )",
                "CREATE UNIQUE INDEX `apiBallotIdAndCreator` ON `ballot` ( `apiBallotId`, `creatorIdentity` )"
            };
        }
    }

    private static class BallotChoiceModel {
        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `ballot_choice` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER , `apiBallotChoiceId` INTEGER , `type` VARCHAR , `name` VARCHAR , `voteCount` INTEGER , `order` INTEGER NOT NULL , `createdAt` BIGINT , `modifiedAt` BIGINT )",
                "CREATE UNIQUE INDEX `apiBallotChoiceId` ON `ballot_choice` ( `ballotId`, `apiBallotChoiceId` )"
            };
        }
    }

    private static class BallotVoteModel {
        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `ballot_vote` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER NOT NULL , `ballotChoiceId` INTEGER NOT NULL , `votingIdentity` VARCHAR NOT NULL , `choice` INTEGER , `createdAt` BIGINT NOT NULL , `modifiedAt` BIGINT NOT NULL );",
                "CREATE INDEX `ballotVotingCount` ON `ballot_vote` ( `ballotChoiceId`, `choice` )",
                "CREATE UNIQUE INDEX `ballotVoteIdentity` ON `ballot_vote` ( `ballotId`, `ballotChoiceId`, `votingIdentity` );"
            };
        }
    }

    private static class IdentityBallotModel {
        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `identity_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR NOT NULL , `ballotId` INTEGER NOT NULL )",
                "CREATE UNIQUE INDEX `identityBallotId` ON `identity_ballot` ( `identity`, `ballotId` )"
            };
        }
    }

    private static class GroupBallotModel {
        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `group_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `groupId` INTEGER NOT NULL , `ballotId` INTEGER NOT NULL )",
                "CREATE UNIQUE INDEX `groupBallotId` ON `group_ballot` ( `groupId`, `ballotId` )"
            };
        }
    }
}
