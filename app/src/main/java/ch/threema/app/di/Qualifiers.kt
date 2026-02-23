package ch.threema.app.di

import org.koin.core.qualifier.named

object Qualifiers {
    /**
     * Warning: the OkHttp base client does NOT enforce OnPrem specific certificate pinning.
     * It must therefore only be used in places where this is acceptable, such as for fetching the OPPF.
     * Otherwise, use the default client, i.e., without specifying a qualifier.
     */
    val okHttpBase = named("ok-http-base")
}
