package ch.threema.storage.databaseupdate;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion7 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;
    private final Context appContext;

    public DatabaseUpdateToVersion7(
        SQLiteDatabase sqLiteDatabase,
        Context appContext
    ) {
        this.sqLiteDatabase = sqLiteDatabase;
        this.appContext = appContext;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "message", "uid")) {
            //update the message model with the uid and move every file to the new filename rule
            sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN uid VARCHAR(50) DEFAULT NULL");
        }

        setMessageUids();
    }

    private void setMessageUids() {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.startsWith(".") && filename.contains("-");
            }
        };

        File appPath = new File(appContext.getExternalFilesDir(null), "data");

        HashMap<Integer, List<File>> fileIndex = new HashMap<Integer, List<File>>();
        for (String path : new String[]{Environment.getExternalStorageDirectory() + "/.threema", Environment.getExternalStorageDirectory() + "/Threema/.threema"}) {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                continue;
            }
            for (File file : pathFile.listFiles(filter)) {
                String[] pieces = file.getName().substring(1).split("-");
                if (pieces.length >= 2) {
                    try {
                        Integer key = Integer.parseInt(pieces[0]);

                        if (!fileIndex.containsKey(key)) {
                            fileIndex.put(key, new ArrayList<File>());
                        }
                        fileIndex.get(key).add(file);
                    } catch (NumberFormatException e) {
                        //do nothing!!
                    }
                }
            }
        }

        Cursor messages = sqLiteDatabase.rawQuery("SELECT id FROM message", null);
        while (messages.moveToNext()) {
            final int id = messages.getInt(0);
            String uid = UUID.randomUUID().toString();

            if (fileIndex.containsKey(id) && !fileIndex.get(id).isEmpty()) {
                for (File ftm : fileIndex.get(id)) {
                    String postFix = ftm.getName().substring(String.valueOf(id).length() + 2);
                    File newFileToMerge = new File(appPath.getPath() + "/." + uid + "-" + postFix);
                    ftm.renameTo(newFileToMerge);
                }
            }

            sqLiteDatabase.rawExecSQL("UPDATE message SET uid = '" + uid + "' WHERE id = " + id);
        }
        messages.close();
    }

    @Override
    public int getVersion() {
        return 7;
    }
}
