/*
 * Copyright (c) 2022 Proton Technologies AG
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

package me.proton.core.observability.domain

import kotlinx.coroutines.launch
import me.proton.core.observability.domain.entity.ObservabilityEvent
import me.proton.core.observability.domain.metrics.ObservabilityData
import me.proton.core.observability.domain.usecase.IsObservabilityEnabled
import me.proton.core.util.kotlin.CoreLogger
import me.proton.core.util.kotlin.CoroutineScopeProvider
import java.time.Instant
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

public class ObservabilityManager @Inject internal constructor(
    private val isObservabilityEnabled: IsObservabilityEnabled,
    private val repository: ObservabilityRepository,
    private val scopeProvider: CoroutineScopeProvider,
    private val timeTracker: ObservabilityTimeTracker,
    private val workerManager: ObservabilityWorkerManager,
) {
    /** Enqueues an event with a given [data] and [timestamp] to be sent at some point in the future.
     * If observability is disabled, the event won't be sent.
     */
    public fun enqueue(data: ObservabilityData, timestamp: Instant = Instant.now()) {
        enqueue(
            ObservabilityEvent(
                timestamp = timestamp,
                data = data
            )
        )
    }

    /** Enqueues an [event] to be sent at some point in the future.
     * If observability is disabled, the event won't be sent
     */
    public fun enqueue(event: ObservabilityEvent) {
        scopeProvider.GlobalIOSupervisedScope.launch {
            enqueueEvent(event)
        }
    }

    private suspend fun enqueueEvent(event: ObservabilityEvent) {
        CoreLogger.d(LogTag.ENQUEUE, "$event")
        if (isObservabilityEnabled()) {
            repository.addEvent(event)
            workerManager.schedule(getSendDelay())

            if (timeTracker.getDurationSinceFirstEvent() == null) {
                timeTracker.setFirstEventNow()
            }
        } else {
            workerManager.cancel()
            repository.deleteAllEvents()
            timeTracker.clear()
        }
    }

    private suspend fun getSendDelay(): Duration {
        suspend fun isMaxDurationExceeded(): Boolean {
            val duration = timeTracker.getDurationSinceFirstEvent()
            return if (duration != null) {
                duration >= MAX_DELAY_MS.milliseconds
            } else false
        }

        return when {
            repository.getEventCount() >= MAX_EVENT_COUNT -> ZERO
            isMaxDurationExceeded() -> ZERO
            else -> MAX_DELAY_MS.milliseconds
        }
    }

    internal companion object {
        internal const val MAX_EVENT_COUNT = 50L
        internal const val MAX_DELAY_MS = 30 * 1000L
    }
}

/** Enqueues an observability data event from the [Result] of executing a [block].
 * The event is recorded if [metricData] is not null.
 **/
public suspend fun <T, R> T.runWithObservability(
    observabilityManager: ObservabilityManager,
    metricData: ((Result<R>) -> ObservabilityData?)?,
    block: suspend T.() -> R
): R = runCatching {
    block()
}.also { result ->
    if (result.exceptionOrNull() !is CancellationException) {
        metricData?.invoke(result)?.let { observabilityManager.enqueue(it) }
    }
}.getOrThrow()
