package utils

import com.android.build.api.dsl.VariantDimension

fun VariantDimension.intBuildConfigField(name: String, value: Int) {
    buildConfigField("int", name, value.toString())
}

fun VariantDimension.stringBuildConfigField(name: String, value: String?) {
    buildConfigField("String", name, if (value != null) "\"$value\"" else "null")
}

fun VariantDimension.booleanBuildConfigField(name: String, value: Boolean) {
    buildConfigField("boolean", name, value.toString())
}

fun VariantDimension.stringArrayBuildConfigField(name: String, value: Array<String>) {
    buildConfigField(
        "String[]",
        name,
        "new String[] {${value.joinToString(separator = ", ") { "\"$it\"" }}}",
    )
}

fun VariantDimension.intArrayBuildConfigField(name: String, value: IntArray) {
    buildConfigField("int[]", name, "{${value.joinToString(separator = ", ")}}")
}

fun VariantDimension.byteArrayBuildConfigField(name: String, value: ByteArray?) {
    buildConfigField(
        "byte[]",
        name,
        if (value != null) {
            "new byte[] {${value.joinToString(separator = ", ") { "(byte) 0x%02x".format(it) }}}"
        } else {
            "null"
        },
    )
}
