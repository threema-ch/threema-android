package ch.threema.app.identitylinks

import ch.threema.app.services.UserService
import ch.threema.app.test.testDispatcherProvider
import ch.threema.domain.protocol.api.LinkMobileNoException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class VerifyMobileNumberUseCaseTest {

    @Test
    fun `verification works for valid code`() = runTest {
        val userServiceMock: UserService = mockk {
            every { mobileLinkingState } returns UserService.LinkingState_PENDING
            every { verifyMobileNumber(any(), any()) } returns true
        }

        val verifyMobileNumberUseCase = VerifyMobileNumberUseCase(
            userService = userServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        val result = verifyMobileNumberUseCase.call(
            verificationCode = "01234567",
        )

        assertEquals(
            expected = VerifyMobileNumberUseCase.VerificationResult.Success,
            actual = result,
        )
        verify(exactly = 1) { userServiceMock.verifyMobileNumber(any(), any()) }
    }

    @Test
    fun `verification works for invalid code with correct format`() = runTest {
        val userServiceMock: UserService = mockk {
            every { mobileLinkingState } returns UserService.LinkingState_PENDING
            every { verifyMobileNumber(any(), any()) } throws LinkMobileNoException("Fail!")
        }

        val verifyMobileNumberUseCase = VerifyMobileNumberUseCase(
            userService = userServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = verifyMobileNumberUseCase.call(
            verificationCode = "01234567",
        )

        assertTrue { result is VerifyMobileNumberUseCase.VerificationResult.Failure }
        verify(exactly = 1) { userServiceMock.verifyMobileNumber(any(), any()) }
    }

    @Test
    fun `verification code is not sent to server if the code is too long`() = runTest {
        val userServiceMock: UserService = mockk {
            every { mobileLinkingState } returns UserService.LinkingState_PENDING
        }

        val verifyMobileNumberUseCase = VerifyMobileNumberUseCase(
            userService = userServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = verifyMobileNumberUseCase.call(
            // The code is too long
            verificationCode = "012345678",
        )

        assertTrue { result is VerifyMobileNumberUseCase.VerificationResult.Failure }
        verify(exactly = 0) { userServiceMock.verifyMobileNumber(any(), any()) }
    }

    @Test
    fun `verification code is not sent to server if the code contains letters`() = runTest {
        val userServiceMock: UserService = mockk {
            every { mobileLinkingState } returns UserService.LinkingState_PENDING
        }

        val verifyMobileNumberUseCase = VerifyMobileNumberUseCase(
            userService = userServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = verifyMobileNumberUseCase.call(
            // The code contains letters
            verificationCode = "0123ABCD",
        )

        assertTrue { result is VerifyMobileNumberUseCase.VerificationResult.Failure }
        verify(exactly = 0) { userServiceMock.verifyMobileNumber(any(), any()) }
    }

    @Test
    fun `verification code is not sent to server if user is not verifying the phone number`() = runTest {
        val userServiceMock: UserService = mockk {
            every { mobileLinkingState } returns UserService.LinkingState_NONE
        }

        val verifyMobileNumberUseCase = VerifyMobileNumberUseCase(
            userService = userServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = verifyMobileNumberUseCase.call(
            verificationCode = "01234567",
        )

        assertTrue { result is VerifyMobileNumberUseCase.VerificationResult.Failure }
        verify(exactly = 0) { userServiceMock.verifyMobileNumber(any(), any()) }
    }

    @Test
    fun `verification code is not sent to server if user has already verified a phone number`() = runTest {
        val userServiceMock: UserService = mockk {
            every { mobileLinkingState } returns UserService.LinkingState_LINKED
        }

        val verifyMobileNumberUseCase = VerifyMobileNumberUseCase(
            userService = userServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = verifyMobileNumberUseCase.call(
            verificationCode = "01234567",
        )

        assertTrue { result is VerifyMobileNumberUseCase.VerificationResult.Failure }
        verify(exactly = 0) { userServiceMock.verifyMobileNumber(any(), any()) }
    }
}
