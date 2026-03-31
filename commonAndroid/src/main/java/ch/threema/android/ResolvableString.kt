package ch.threema.android

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
fun interface ResolvableString {
    fun get(context: Context): String
}

@Immutable
data class ResolvedString(val string: String) : ResolvableString {
    override fun get(context: Context) = string
}

@Immutable
data class ResourceIdString(@StringRes val resId: Int) : ResolvableString {
    override fun get(context: Context) = context.resources.getString(resId)
}

fun String.toResolvedString() = ResolvedString(this)
