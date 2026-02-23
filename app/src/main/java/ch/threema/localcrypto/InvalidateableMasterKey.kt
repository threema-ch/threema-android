package ch.threema.localcrypto

interface InvalidateableMasterKey : MasterKey {
    fun invalidate()
}
