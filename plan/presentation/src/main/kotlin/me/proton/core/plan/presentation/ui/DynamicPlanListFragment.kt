/*
 * Copyright (c) 2023 Proton Technologies AG
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

package me.proton.core.plan.presentation.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.domain.entity.UserId
import me.proton.core.plan.domain.entity.DynamicDecoration
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.presentation.R
import me.proton.core.plan.presentation.databinding.FragmentDynamicPlanListBinding
import me.proton.core.plan.presentation.entity.SelectedPlan
import me.proton.core.plan.presentation.entity.getSelectedPlan
import me.proton.core.plan.presentation.view.DynamicPlanCardView
import me.proton.core.plan.presentation.view.DynamicPlanView
import me.proton.core.plan.presentation.view.toView
import me.proton.core.plan.presentation.viewmodel.DynamicPlanListViewModel
import me.proton.core.plan.presentation.viewmodel.DynamicPlanListViewModel.Action
import me.proton.core.plan.presentation.viewmodel.DynamicPlanListViewModel.State
import me.proton.core.presentation.ui.ProtonFragment
import me.proton.core.presentation.utils.formatCentsPriceDefaultLocale
import me.proton.core.presentation.utils.getUserMessage
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding
import kotlin.math.abs

@Suppress("TooManyFunctions")
@AndroidEntryPoint
class DynamicPlanListFragment : ProtonFragment(R.layout.fragment_dynamic_plan_list) {

    private val binding by viewBinding(FragmentDynamicPlanListBinding::bind)
    private val viewModel by viewModels<DynamicPlanListViewModel>()

    private var onPlanSelected: ((SelectedPlan) -> Unit)? = null

    fun setOnPlanSelected(onPlanSelected: (SelectedPlan) -> Unit) {
        this.onPlanSelected = onPlanSelected
    }

    fun setUserId(userId: UserId) {
        viewModel.perform(Action.SetUserId(userId))
    }

    fun setCurrency(currency: String) {
        viewModel.perform(Action.SetCurrency(currency))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.state.onEach {
            when (it) {
                is State.Loading -> onLoading()
                is State.Error -> onError(it.error)
                is State.Success -> onSuccess(it)
            }
        }.launchIn(lifecycleScope)

        binding.retry.onClick { viewModel.perform(Action.Load) }
    }

    private fun onLoading() {
        showLoading(true)
    }

    private fun onError(error: Throwable?) = with(binding) {
        showLoading(false)
        val message = error?.getUserMessage(resources)
        showError(message ?: getString(R.string.presentation_error_general))
    }

    private fun onSuccess(result: State.Success) {
        showLoading(false)
        showPlans(result.plans, result.filter.cycle, result.filter.currency)
    }

    private fun showLoading(loading: Boolean) = with(binding) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        errorLayout.visibility = View.GONE
        binding.plans.removeAllViews()
    }

    private fun showError(message: String) = with(binding) {
        errorLayout.visibility = View.VISIBLE
        error.text = message
    }

    private fun showPlans(plans: List<DynamicPlan>, cycle: Int, currency: String?) {
        binding.plans.removeAllViews()
        plans.forEach { plan ->
            val cardView = DynamicPlanCardView(requireContext())
            val selectedPlan = plan.getSelectedPlan(cycle, currency)
            cardView.planView.setPlan(plan, cycle, currency)
            cardView.planView.setOnButtonClickListener { onPlanSelected?.invoke(selectedPlan) }
            binding.plans.addView(cardView)
        }
    }

    private fun DynamicPlanView.setPlan(plan: DynamicPlan, cycle: Int, currency: String?) {
        val instance = plan.instances[cycle]
        val price = instance?.price?.get(currency)
        id = abs(plan.name.hashCode())
        title = plan.title
        description = plan.description
        starred = plan.decorations.filterIsInstance<DynamicDecoration.Star>().isNotEmpty()
        priceText = price?.current?.toDouble()?.formatCentsPriceDefaultLocale(price.currency)
        priceCycle = instance?.description
        isCollapsable = true
        entitlements.removeAllViews()
        plan.entitlements.forEach { entitlements.addView(it.toView(context)) }
        buttonTextIsVisible = true
        buttonText = String.format(context.getString(R.string.plans_get_proton), plan.title)
    }
}
