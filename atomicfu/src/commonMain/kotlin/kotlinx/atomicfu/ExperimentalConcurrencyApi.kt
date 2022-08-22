package kotlinx.atomicfu

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Experimental API that is not intended for stable uses. " +
            "Atomicfu is itself the experimental library, so experimental API is definitely going to break between releases, " +
            "providing incompatibilities between K/N versions"
)
public annotation class ExperimentalConcurrencyApi
