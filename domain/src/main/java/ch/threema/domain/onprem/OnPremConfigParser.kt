/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.onprem

import ch.threema.base.utils.Base64
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.TimeProvider
import ch.threema.common.plus
import ch.threema.common.toIntArray
import ch.threema.common.toJSONObjectList
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl
import java.io.IOException
import java.text.ParseException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.json.JSONException
import org.json.JSONObject

private val logger = LoggingUtil.getThreemaLogger("OnPremConfigParser")

class OnPremConfigParser(
    private val timeProvider: TimeProvider = TimeProvider.default,
) {
    @Throws(OnPremConfigParseException::class)
    fun parse(obj: JSONObject): OnPremConfig =
        try {
            OnPremConfig(
                validUntil = timeProvider.get() + getRefreshDuration(obj),
                license = parseLicense(obj.getJSONObject("license")),
                domains = parseDomains(obj.optJSONObject("domains")),
                chat = parseChatConfig(obj.getJSONObject("chat")),
                directory = parseDirectoryConfig(obj.getJSONObject("directory")),
                blob = parseBlobConfig(obj.getJSONObject("blob")),
                work = parseWorkConfig(obj.getJSONObject("work")),
                avatar = parseAvatarConfig(obj.getJSONObject("avatar")),
                safe = parseSafeConfig(obj.getJSONObject("safe")),
                web = parseWebConfig(obj.optJSONObject("web")),
                mediator = parseMediatorConfig(obj.optJSONObject("mediator")),
                maps = parseMapConfig(obj.optJSONObject("maps")),
            )
        } catch (e: IOException) {
            throw OnPremConfigParseException(e)
        } catch (e: JSONException) {
            throw OnPremConfigParseException(e)
        } catch (e: ParseException) {
            throw OnPremConfigParseException(e)
        }

    private fun getRefreshDuration(obj: JSONObject): Duration {
        val refreshDuration = obj.getInt("refresh").seconds
        if (refreshDuration < minRefreshDuration) {
            logger.warn("Invalid refresh duration provided: {}; using {} as fallback", refreshDuration, minRefreshDuration)
            return minRefreshDuration
        }
        return refreshDuration
    }

    private fun parseLicense(obj: JSONObject): OnPremLicense =
        OnPremLicense(
            id = obj.getString("id"),
            expires = Instant.from(
                LocalDate.parse(obj.getString("expires"))
                    .atStartOfDay()
                    .plusDays(1)
                    .atOffset(ZoneOffset.UTC),
            ),
            count = obj.getInt("count"),
        )

    private fun parseDomains(obj: JSONObject?): OnPremConfigDomains? {
        val rules = obj?.getJSONArray("rules") ?: return null
        return OnPremConfigDomains(
            rules = rules
                .toJSONObjectList()
                .mapNotNull { ruleObject ->
                    val matchModeString = ruleObject.getString("matchMode")
                    val matchMode = OnPremConfigDomainRuleMatchMode.fromStringOrNull(matchModeString)
                    if (matchMode == null) {
                        logger.warn("Unknown match mode '{}' found, ignoring domain rule", matchModeString)
                        return@mapNotNull null
                    }
                    OnPremConfigDomainRule(
                        fqdn = ruleObject.getString("fqdn"),
                        matchMode = matchMode,
                        spkis = ruleObject.optJSONArray("spkis")
                            ?.toJSONObjectList()
                            ?.mapNotNull { spkiObject ->
                                val algorithmString = spkiObject.getString("algorithm")
                                val algorithm = OnPremConfigDomainRuleSpkiAlgorithm.fromStringOrNull(algorithmString)
                                if (algorithm != null) {
                                    OnPremConfigDomainRuleSpki(
                                        value = spkiObject.getString("value"),
                                        algorithm = algorithm,
                                    )
                                } else {
                                    logger.warn("Unknown algorithm '{}' found, ignoring domain rule spki", algorithmString)
                                    null
                                }
                            },
                    )
                },
        )
    }

    private fun parseChatConfig(obj: JSONObject): OnPremConfigChat =
        OnPremConfigChat(
            hostname = obj.getString("hostname"),
            ports = obj.getJSONArray("ports").toIntArray(),
            publicKey = Base64.decode(obj.getString("publicKey")),
        )

    private fun parseDirectoryConfig(obj: JSONObject): OnPremConfigDirectory =
        OnPremConfigDirectory(obj.getString("url"))

    private fun parseBlobConfig(obj: JSONObject): OnPremConfigBlob =
        OnPremConfigBlob(
            uploadUrl = obj.getString("uploadUrl"),
            downloadUrl = BlobUrl(obj.getString("downloadUrl")),
            doneUrl = BlobUrl(obj.getString("doneUrl")),
        )

    private fun parseAvatarConfig(obj: JSONObject): OnPremConfigAvatar =
        OnPremConfigAvatar(url = obj.getString("url"))

    private fun parseSafeConfig(obj: JSONObject): OnPremConfigSafe =
        OnPremConfigSafe(url = obj.getString("url"))

    private fun parseWorkConfig(obj: JSONObject): OnPremConfigWork =
        OnPremConfigWork(url = obj.getString("url"))

    private fun parseWebConfig(obj: JSONObject?): OnPremConfigWeb? =
        obj?.let {
            OnPremConfigWeb(
                url = obj.getString("url"),
                overrideSaltyRtcHost = obj.optString("overrideSaltyRtcHost"),
                overrideSaltyRtcPort = obj.optInt("overrideSaltyRtcPort"),
            )
        }

    private fun parseMediatorConfig(obj: JSONObject?): OnPremConfigMediator? =
        obj?.let {
            OnPremConfigMediator(
                url = DeviceGroupUrl(obj.getString("url")),
                blob = parseBlobConfig(obj.getJSONObject("blob")),
            )
        }

    private fun parseMapConfig(obj: JSONObject?): OnPremConfigMaps? =
        obj?.let {
            OnPremConfigMaps(
                styleUrl = obj.getString("styleUrl"),
                poiNamesUrl = MapPoiNamesUrl(obj.getString("poiNamesUrl")),
                poiAroundUrl = MapPoiAroundUrl(obj.getString("poiAroundUrl")),
            )
        }

    companion object {
        private val minRefreshDuration = 30.minutes
    }
}
