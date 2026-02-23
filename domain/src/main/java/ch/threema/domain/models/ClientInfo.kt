package ch.threema.domain.models

interface ClientInfo {
    val appVersion: String
    val appLocale: String
    val deviceModel: String
    val osVersion: String
}
