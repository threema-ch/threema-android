package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class DatabaseUpdateToVersion65 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion65(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        createGroupInviteTable();
        createOutgoingGroupJoinRequestTable();
        createIncomingGroupJoinRequestModelFactory();
    }

    private void createGroupInviteTable() {
        sqLiteDatabase.rawExecSQL("CREATE TABLE `group_invite_model` ( " +
            "`group_invite_index_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`group_id` INTEGER, " +
            "`default_flag` BOOLEAN, " +
            "`token` VARCHAR, " +
            "`invite_name` TEXT, " +
            "`original_group_name` TEXT, " +
            "`manual_confirmation` BOOLEAN, " +
            "`expiration_date` DATETIME NULL, " +
            "`is_invalidated` BOOLEAN FALSE " +
            ")");
    }

    private void createOutgoingGroupJoinRequestTable() {
        sqLiteDatabase.rawExecSQL("CREATE TABLE IF NOT EXISTS `group_join_request` ( " +
            "`outgoing_request_index_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`token` VARCHAR, " +
            "`group_name` TEXT, " +
            "`message` TEXT, " +
            "`admin_identity` VARCHAR, " +
            "`request_time` DATETIME, " +
            "`status` VARCHAR, " +
            "`group_api_id` INTEGER NULL " +
            ")");
    }

    private void createIncomingGroupJoinRequestModelFactory() {
        sqLiteDatabase.rawExecSQL("CREATE TABLE IF NOT EXISTS`incoming_group_join_request` ( " +
            "`incoming_request_index_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`group_invite` INTEGER, " +
            "`message` TEXT, " +
            "`requesting_identity` VARCHAR, " +
            "`request_time` DATETIME, " +
            "`response_status` VARCHAR " +
            ")");
    }

    @Override
    public int getVersion() {
        return 65;
    }
}
