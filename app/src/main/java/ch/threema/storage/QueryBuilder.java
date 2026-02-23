package ch.threema.storage;

import net.zetetic.database.sqlcipher.SQLiteQueryBuilder;

public class QueryBuilder extends SQLiteQueryBuilder {
    private int whereCount = 0;

    @Override
    public void appendWhere(CharSequence inWhere) {
        inWhere = "(" + inWhere + ")";
        if (this.whereCount > 0) {
            //append a AND
            inWhere = " AND " + inWhere;
        }
        this.whereCount++;
        super.appendWhere(inWhere);
    }
}
