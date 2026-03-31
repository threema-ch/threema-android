package ch.threema.app.di

import org.koin.core.qualifier.named

object Qualifiers {
    /**
     * Warning: the OkHttp base client does NOT enforce any OnPrem-specific certificate pinning.
     * It must therefore only be used in places where this is acceptable, e.g. for building other OkHttpClients.
     * Otherwise, use the default client, i.e., without specifying a qualifier.
     */
    val okHttpBase = named("ok-http-base")
}
