package ch.threema.domain.libthreema

import ch.threema.libthreema.ScryptParameters

object LibthreemaJavaBridge {

    /**
     * Fixes compiler error: ScryptParameters(byte,int,int,byte) has private access in ScryptParameters.
     *
     * Parameter default values as recommended by [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#scrypt)
     *
     * @see [ch.threema.libthreema.ScryptParameters]
     */
    @JvmStatic
    @JvmOverloads
    fun createScryptParameters(
        logMemoryCost: Byte = 16,
        blockSize: Int = 8,
        parallelism: Int = 1,
        outputLength: Byte,
    ) = ScryptParameters(
        logMemoryCost = logMemoryCost.toUByte(),
        blockSize = blockSize.toUInt(),
        parallelism = parallelism.toUInt(),
        outputLength = outputLength.toUByte(),
    )
}
