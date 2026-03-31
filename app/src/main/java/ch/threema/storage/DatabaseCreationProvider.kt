package ch.threema.storage

fun interface DatabaseCreationProvider {
    /**
     * @return table and index creation statements, used for bootstrapping
     * when the database is created for the first time or recreated.
     */
    fun getCreationStatements(): Array<String>
}
