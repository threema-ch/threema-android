package ch.threema.app.threemasafe.usecases

import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.threemasafe.usecases.CheckBadPasswordUseCase.Result.ACCEPTABLE_PASSWORD
import ch.threema.app.threemasafe.usecases.CheckBadPasswordUseCase.Result.BAD_PASSWORD
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckBadPasswordUseCaseTest {

    private lateinit var appRestrictionsMock: AppRestrictions
    private lateinit var checkBadPasswordUseCase: CheckBadPasswordUseCase

    @BeforeTest
    fun setUp() {
        appRestrictionsMock = mockk {
            every { getSafePasswordPattern() } returns null
        }
        checkBadPasswordUseCase = CheckBadPasswordUseCase(
            appContext = mockk {
                every { assets } returns mockk {
                    every { open("passwords/bad_passwords.txt") } answers { BAD_PASSWORDS.byteInputStream() }
                }
            },
            appRestrictions = appRestrictionsMock,
        )
    }

    @Test
    fun `short numeric-only passwords are bad`() {
        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("1234"))
        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("1234567890"))
        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("123456789012345"))
        assertEquals(ACCEPTABLE_PASSWORD, checkBadPasswordUseCase.call("1234567890123456"))
        assertEquals(ACCEPTABLE_PASSWORD, checkBadPasswordUseCase.call("12345678901234567890"))
    }

    @Test
    fun `passwords consisting of single repeating character are bad`() {
        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("aaaaaaaaaaaa"))
        assertEquals(ACCEPTABLE_PASSWORD, checkBadPasswordUseCase.call("aaaaaaaaaaab"))
    }

    @Test
    fun `passwords from the bad list are bad, case-insensitive`() {
        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("Password1"))
        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("passWord2"))
        assertEquals(ACCEPTABLE_PASSWORD, checkBadPasswordUseCase.call("Password7"))
    }

    @Test
    fun `app restrictions pattern is used, regular rules are ignored`() {
        every { appRestrictionsMock.getSafePasswordPattern() } returns "X.*X".toPattern()

        assertEquals(BAD_PASSWORD, checkBadPasswordUseCase.call("X123Y"))
        assertEquals(ACCEPTABLE_PASSWORD, checkBadPasswordUseCase.call("X123X"))
        assertEquals(ACCEPTABLE_PASSWORD, checkBadPasswordUseCase.call("XXX"))
    }

    companion object {
        private const val BAD_PASSWORDS = "Password1\nPassword2\nPassword3\n"
    }
}
