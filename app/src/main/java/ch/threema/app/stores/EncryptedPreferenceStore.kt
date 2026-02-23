package ch.threema.app.stores

interface EncryptedPreferenceStore : PreferenceStore {
    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun remove(keys: Set<String>) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Int) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Boolean) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Long) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun save(key: String, value: Float) {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getLong(key: String, defaultValue: Long): Long {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getInt(key: String, defaultValue: Int): Int {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getFloat(key: String, defaultValue: Float): Float {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        throw UnsupportedOperationException()
    }

    /**
     * Currently not supported.
     * Implement it if you need it.
     */
    override fun getStringSet(key: String): Set<String>? {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFS_PRIVATE_KEY = "private_key"
        const val PREFS_MD_PROPERTIES = "md_properties"
    }
}
