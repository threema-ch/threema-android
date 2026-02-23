package ch.threema.domain.protocol

private const val USER_AGENT = "Threema"

fun getUserAgent() = USER_AGENT

fun getUserAgent(version: Version) = "$USER_AGENT/${version.versionString}"
