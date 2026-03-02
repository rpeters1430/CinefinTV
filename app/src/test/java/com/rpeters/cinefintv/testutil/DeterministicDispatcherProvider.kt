package com.rpeters.cinefintv.testutil

import com.rpeters.cinefintv.data.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

class DeterministicDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val unconfined: CoroutineDispatcher = dispatcher
}
