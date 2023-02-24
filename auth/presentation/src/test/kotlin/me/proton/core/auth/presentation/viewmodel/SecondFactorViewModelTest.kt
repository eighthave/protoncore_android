/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.auth.presentation.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.auth.domain.AccountWorkflowHandler
import me.proton.core.auth.domain.entity.ScopeInfo
import me.proton.core.auth.domain.usecase.PerformSecondFactor
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.auth.presentation.entity.SessionResult
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.test.android.ArchTest
import me.proton.core.test.kotlin.CoroutinesTest
import me.proton.core.test.kotlin.UnconfinedCoroutinesTest
import me.proton.core.test.kotlin.assertIs
import me.proton.core.test.kotlin.flowTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class SecondFactorViewModelTest : ArchTest by ArchTest(), CoroutinesTest by UnconfinedCoroutinesTest() {
    // region mocks
    private val accountManager = mockk<AccountWorkflowHandler>(relaxed = true)
    private val sessionProvider = mockk<SessionProvider>(relaxed = true)
    private val performSecondFactor = mockk<PerformSecondFactor>()
    private val postLoginAccountSetup = mockk<PostLoginAccountSetup>(relaxed = true)

    private val testSessionResult = mockk<SessionResult>(relaxed = true)
    private val testScopeInfo = mockk<ScopeInfo>(relaxed = true)
    // endregion

    // region test data
    private val testUserId = UserId("test-user-id")
    private val testSessionId = SessionId("test-session-id")
    private val testSecondFactorCode = "123456"
    private val testLoginPassword = "123456"
    private val success = PostLoginAccountSetup.Result.UserUnlocked(testUserId)
    private val twoPassNeeded = PostLoginAccountSetup.Result.Need.TwoPassMode(testUserId)

    // endregion

    private lateinit var viewModel: SecondFactorViewModel

    @Before
    fun beforeEveryTest() {
        viewModel = SecondFactorViewModel(
            accountManager,
            performSecondFactor,
            postLoginAccountSetup,
            sessionProvider
        )
        coEvery { sessionProvider.getSessionId(any()) } returns testSessionId
        every { testSessionResult.sessionId } returns testSessionId.id
        every { testSessionResult.isTwoPassModeNeeded } returns false
    }

    @Test
    fun `submit 2fa happy flow states are handled correctly`() = coroutinesTest {
        // GIVEN
        val requiredAccountType = AccountType.Internal
        coEvery { performSecondFactor.invoke(testSessionId, testSecondFactorCode) } returns testScopeInfo
        coEvery { postLoginAccountSetup.invoke(any(), any(), any(), any(), any(), any()) } returns success
        flowTest(viewModel.state) {
            // WHEN
            viewModel.startSecondFactorFlow(
                userId = testUserId,
                encryptedPassword = testLoginPassword,
                requiredAccountType = requiredAccountType,
                isTwoPassModeNeeded = false,
                secondFactorCode = testSecondFactorCode
            )

            // THEN
            assertIs<SecondFactorViewModel.State.Processing>(awaitItem())
            assertIs<SecondFactorViewModel.State.AccountSetupResult>(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit 2fa two pass mode flow states are handled correctly`() = coroutinesTest {
        // GIVEN
        val requiredAccountType = AccountType.Internal
        every { testSessionResult.isTwoPassModeNeeded } returns true
        coEvery { performSecondFactor.invoke(testSessionId, testSecondFactorCode) } returns testScopeInfo
        coEvery { postLoginAccountSetup.invoke(any(), any(), any(), any(), any(), any()) } returns twoPassNeeded
        // WHEN
        flowTest(viewModel.state) {
            viewModel.startSecondFactorFlow(
                userId = testUserId,
                encryptedPassword = testLoginPassword,
                requiredAccountType = requiredAccountType,
                isTwoPassModeNeeded = true,
                secondFactorCode = testSecondFactorCode
            )

            // THEN
            val accountManagerArguments = slot<SessionId>()

            coVerify(exactly = 1) { accountManager.handleSecondFactorSuccess(capture(accountManagerArguments), any()) }
            coVerify(exactly = 0) { accountManager.handleSecondFactorFailed(any()) }

            assertIs<SecondFactorViewModel.State.Processing>(awaitItem())
            assertIs<SecondFactorViewModel.State.AccountSetupResult>(awaitItem())

            assertEquals(testSessionId, accountManagerArguments.captured)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit 2fa without corresponding sessionId return Unrecoverable`() = coroutinesTest {
        // GIVEN
        val requiredAccountType = AccountType.Internal
        coEvery { sessionProvider.getSessionId(any()) } returns null
        // WHEN
        flowTest(viewModel.state) {
            viewModel.startSecondFactorFlow(
                userId = testUserId,
                encryptedPassword = testLoginPassword,
                requiredAccountType = requiredAccountType,
                isTwoPassModeNeeded = false,
                secondFactorCode = testSecondFactorCode
            )

            // THEN
            assertIs<SecondFactorViewModel.State.Processing>(awaitItem())
            assertIs<SecondFactorViewModel.State.Error.Unrecoverable>(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stop 2fa invokes failed on account manager`() = coroutinesTest {
        // WHEN
        viewModel.stopSecondFactorFlow(testUserId)
        // THEN
        val arguments = slot<SessionId>()
        coVerify(exactly = 1) { accountManager.handleSecondFactorFailed(capture(arguments)) }
        coVerify(exactly = 0) { accountManager.handleSecondFactorSuccess(any(), any()) }
    }

    @Test
    fun `stop 2fa without sessionId`() = coroutinesTest {
        // GIVEN
        val requiredAccountType = AccountType.Internal
        coEvery { sessionProvider.getSessionId(any()) } returns null
        // WHEN
        viewModel.startSecondFactorFlow(
            userId = testUserId,
            encryptedPassword = testLoginPassword,
            requiredAccountType = requiredAccountType,
            isTwoPassModeNeeded = false,
            secondFactorCode = testSecondFactorCode
        )
        viewModel.stopSecondFactorFlow(testUserId)
        // THEN
        val arguments = slot<SessionId>()
        coVerify(exactly = 0) { accountManager.handleSecondFactorFailed(capture(arguments)) }
        coVerify(exactly = 0) { accountManager.handleSecondFactorSuccess(any(), any()) }
    }

    @Test
    fun `stop 2fa without previously call startSecondFactorFlow`() = coroutinesTest {
        // WHEN
        viewModel.stopSecondFactorFlow(testUserId)
        // THEN
        val arguments = slot<SessionId>()
        coVerify(exactly = 1) { accountManager.handleSecondFactorFailed(capture(arguments)) }
        coVerify(exactly = 0) { accountManager.handleSecondFactorSuccess(any(), any()) }
    }
}
