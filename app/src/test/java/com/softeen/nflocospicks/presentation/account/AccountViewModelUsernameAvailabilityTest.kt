package com.softeen.nflocospicks.presentation.account

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.UpdateUserProfileUseCase
import com.softeen.nflocospicks.domain.usecase.UploadProfilePhotoUseCase
import com.softeen.nflocospicks.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The debounced `distinctUntilChanged` + `flatMapLatest` username-availability
 * pipeline in [AccountViewModel]. Uses a [StandardTestDispatcher] (not the
 * Unconfined one the rest of the AccountViewModel tests use) so the 400 ms
 * `debounce` can be driven with `advanceTimeBy`. Every `runTest` is bound to the
 * rule's dispatcher so the ViewModel's `viewModelScope` and the test share one
 * scheduler — otherwise `advanceTimeBy` would advance a different clock.
 *
 * Note: `usernameAvailability` is written by an always-on `launchIn(viewModelScope)`
 * collector (not a `WhileSubscribed` `stateIn`), so the pipeline runs without an
 * external collector — the `coVerify` on `isUsernameAvailable` confirms this.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelUsernameAvailabilityTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val userRepository = mockk<UserRepository>()
    private val updateProfileUseCase = mockk<UpdateUserProfileUseCase>()
    private val uploadPhotoUseCase = mockk<UploadProfilePhotoUseCase>()
    private val logger = mockk<AppLogger>(relaxed = true)

    private fun viewModel() = AccountViewModel(userRepository, updateProfileUseCase, uploadPhotoUseCase, logger)

    private fun test(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(mainDispatcherRule.testDispatcher, testBody = block)

    private companion object {
        const val DEBOUNCE = 401L
    }

    @Test
    fun `a blank candidate resolves to Unknown without hitting the repository`() = test {
        val vm = viewModel()
        vm.onUsernameChanged("   ", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()

        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Unknown)
        coVerify(exactly = 0) { userRepository.isUsernameAvailable(any(), any()) }
    }

    @Test
    fun `a candidate equal to the current username (case-insensitive) resolves to Unchanged`() = test {
        val vm = viewModel()
        vm.onUsernameChanged("  OldName ", currentUsername = "oldname", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()

        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Unchanged)
        coVerify(exactly = 0) { userRepository.isUsernameAvailable(any(), any()) }
    }

    @Test
    fun `an available candidate goes Checking then Available`() = test {
        // A repo flow that takes some virtual time to answer, so the transient
        // Checking state is observable on the StateFlow before Available lands.
        every { userRepository.isUsernameAvailable("newname", "uid") } returns
            flow { delay(50); emit(true) }
        val vm = viewModel()

        vm.onUsernameChanged("newname", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Checking)

        advanceTimeBy(60); runCurrent()
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Available)
    }

    @Test
    fun `a taken candidate resolves to Taken`() = test {
        every { userRepository.isUsernameAvailable("taken", "uid") } returns flowOf(false)
        val vm = viewModel()

        vm.onUsernameChanged("taken", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()

        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Taken)
    }

    @Test
    fun `rapid edits only trigger one availability check for the final value`() = test {
        every { userRepository.isUsernameAvailable(any(), any()) } returns flowOf(true)
        val vm = viewModel()

        vm.onUsernameChanged("a", currentUsername = "old", uid = "uid")
        advanceTimeBy(100)
        vm.onUsernameChanged("ab", currentUsername = "old", uid = "uid")
        advanceTimeBy(100)
        vm.onUsernameChanged("abc", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()

        coVerify(exactly = 1) { userRepository.isUsernameAvailable("abc", "uid") }
        coVerify(exactly = 0) { userRepository.isUsernameAvailable("a", "uid") }
        coVerify(exactly = 0) { userRepository.isUsernameAvailable("ab", "uid") }
    }

    @Test
    fun `a new keystroke past the debounce cancels an in-flight check and switches to the newer one`() = test {
        every { userRepository.isUsernameAvailable("first", "uid") } returns flow { delay(1_000); emit(true) }
        every { userRepository.isUsernameAvailable("second", "uid") } returns flowOf(false)
        val vm = viewModel()

        vm.onUsernameChanged("first", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent() // "first" check is now in flight (still delaying)
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Checking)

        vm.onUsernameChanged("second", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); advanceUntilIdle()

        // flatMapLatest cancelled the "first" flow; only "second" resolves.
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Taken)
        coVerify(exactly = 1) { userRepository.isUsernameAvailable("second", "uid") }
    }

    @Test
    fun `clearing the field mid-check cancels it and returns to Unknown`() = test {
        every { userRepository.isUsernameAvailable("half", "uid") } returns flow { delay(1_000); emit(true) }
        val vm = viewModel()

        vm.onUsernameChanged("half", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Checking)

        vm.onUsernameChanged("", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); advanceUntilIdle()

        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Unknown)
    }

    @Test
    fun `a failing availability check resolves to Error and keeps the pipeline alive for later queries`() = test {
        every { userRepository.isUsernameAvailable("boom", "uid") } returns flow { throw RuntimeException("permission denied") }
        every { userRepository.isUsernameAvailable("ok", "uid") } returns flowOf(true)
        val vm = viewModel()

        vm.onUsernameChanged("boom", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Error)

        vm.onUsernameChanged("ok", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); runCurrent()
        assertThat(vm.usernameAvailability.value).isEqualTo(UsernameAvailability.Available)
    }

    @Test
    fun `the candidate is normalized (trimmed and lowercased) before the repository check`() = test {
        every { userRepository.isUsernameAvailable("newname", "uid") } returns flowOf(true)
        val vm = viewModel()

        vm.onUsernameChanged("  NewName  ", currentUsername = "old", uid = "uid")
        advanceTimeBy(DEBOUNCE); advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.isUsernameAvailable("newname", "uid") }
    }
}
