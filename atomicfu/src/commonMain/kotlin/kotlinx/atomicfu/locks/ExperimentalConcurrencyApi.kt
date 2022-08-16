package kotlinx.atomicfu.locks

import kotlin.RequiresOptIn.Level


@RequiresOptIn(
    level = Level.WARNING,
    message = "Experimental API that is not intended for stable uses. " +
            "Atomicfu is itself experimental library, so experimental API is definitely going to break between releases, " +
            "providing incompatibilities for K/N versions"
)
public annotation class ExperimentalConcurrencyApi
