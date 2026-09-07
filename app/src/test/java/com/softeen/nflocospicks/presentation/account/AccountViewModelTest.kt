package com.softeen.nflocospicks.presentation.account

import android.app.Activity
import android.net.Uri
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.UpdateUserProfileUseCase
import com.softeen.nflocospicks.domain.usecase.UploadProfilePhotoUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val userRepository = mockk<UserRepository>()
    private val updateProfileUseCase = mockk<UpdateUserProfileUseCase>()
    private val uploadPhotoUseCase = mockk<UploadProfilePhotoUseCase>()
    private val logger = mockk<AppLogger>(relaxed = true)

    private fun viewModel() = AccountViewModel(userRepository, updateProfileUseCase, uploadPhotoUseCase, logger)

    @Test
    fun `saveProfile success logs analytics event`() = runTest(coroutineRule.dispatcher) {
        coEvery { updateProfileUseCase(any(), any(), any()) } returns Result.success(Unit)
        val vm = viewModel()

        vm.saveProfile("uid", "user", "display")

        verify { logger.logEvent(match { it.name == "profile_saved" }) }
    }

    @Test
    fun `uploadPhoto success logs analytics event`() = runTest(coroutineRule.dispatcher) {
        val uri = mockk<Uri>()
        coEvery { uploadPhotoUseCase("uid", uri) } returns Result.success("url")
        val vm = viewModel()

        vm.uploadPhoto("uid", uri)

        verify { logger.logEvent(match { it.name == "profile_photo_uploaded" }) }
    }

    @Test
    fun `sendEmailLink success logs analytics event`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.sendSignInLinkToEmail("email") } returns Result.success(Unit)
        val vm = viewModel()

        vm.sendEmailLink("email")

        verify { logger.logEvent(match { it.name == "account_email_link_sent" }) }
    }

    @Test
    fun `verifyPhoneLinkCode with no active verification session errors immediately`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()

        vm.verifyPhoneLinkCode("123456")

        assertThat(vm.phoneLinkState.value)
            .isEqualTo(PhoneLinkState.Error(AuthError.VERIFICATION_SESSION_EXPIRED))
        verify(exactly = 0) { logger.logEvent(match { it.name == "phone_link_verified" }) }
    }

    @Test
    fun `startPhoneLink CodeSent then a successful verify logs the event and returns to Idle`() =
        runTest(coroutineRule.dispatcher) {
            val activity = mockk<Activity>()
            every { userRepository.linkPhoneNumber(activity, "+15550001") } returns
                flowOf(PhoneVerificationEvent.CodeSent("verif-id"))
            coEvery { userRepository.linkPhoneCredential("verif-id", "123456") } returns Result.success(Unit)
            val vm = viewModel()

            vm.startPhoneLink(activity, "+15550001")
            assertThat(vm.phoneLinkState.value).isEqualTo(PhoneLinkState.CodeSent("+15550001"))

            vm.verifyPhoneLinkCode("123456")

            assertThat(vm.phoneLinkState.value).isEqualTo(PhoneLinkState.Idle)
            verify(exactly = 1) { logger.logEvent(match { it.name == "phone_link_verified" }) }
        }

    @Test
    fun `a failed phone-link verify maps to an error state and does not log`() =
        runTest(coroutineRule.dispatcher) {
            val activity = mockk<Activity>()
            every { userRepository.linkPhoneNumber(activity, any()) } returns
                flowOf(PhoneVerificationEvent.CodeSent("verif-id"))
            coEvery { userRepository.linkPhoneCredential("verif-id", "000000") } returns
                Result.failure(RuntimeException("bad code"))
            val vm = viewModel()

            vm.startPhoneLink(activity, "+15550001")
            vm.verifyPhoneLinkCode("000000")

            assertThat(vm.phoneLinkState.value)
                .isEqualTo(PhoneLinkState.Error(AuthError.LINK_PHONE_FAILED))
            verify(exactly = 0) { logger.logEvent(match { it.name == "phone_link_verified" }) }
        }
}
