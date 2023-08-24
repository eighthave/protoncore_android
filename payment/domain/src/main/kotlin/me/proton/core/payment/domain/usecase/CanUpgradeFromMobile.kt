/*
 * Copyright (c) 2023 Proton AG
 * This file is part of Proton AG and ProtonCore.
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

package me.proton.core.payment.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.plan.domain.SupportUpgradePaidPlans
import javax.inject.Inject

public class CanUpgradeFromMobile @Inject constructor(
    @SupportUpgradePaidPlans public val supportPaidPlans: Boolean,
    private val getAvailablePaymentProviders: GetAvailablePaymentProviders
) {

    public suspend operator fun invoke(userId: UserId? = null): Boolean {
        if (!supportPaidPlans) {
            return false
        }
        val paymentProviders = getAvailablePaymentProviders().filter {
            // It's not possible to setup PayPal during signup, from mobile app.
            it != PaymentProvider.PayPal
        }
        return paymentProviders.isNotEmpty()
    }
}
