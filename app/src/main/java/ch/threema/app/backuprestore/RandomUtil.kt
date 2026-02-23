package ch.threema.app.backuprestore

import java.util.concurrent.ThreadLocalRandom

object RandomUtil {
    /**
     * Get an iterator for obtaining distinct non-cryptographically safe random positive integers
     */
    @JvmStatic
    fun getDistinctRandomIterator(): Iterator<Int> {
        return generateSequence {
            ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE)
        }.distinct().iterator()
    }
}
