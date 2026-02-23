package utils

import com.android.build.api.dsl.VariantDimension

fun VariantDimension.stringResValue(name: String, value: String) {
    resValue("string", name, value)
}
