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

package ch.threema.domain.onprem;

public class OnPremConfigTestData {
    protected static final String PUBLIC_KEY = "jae1lgwR3W7YyKiGQlsbdqObG13FR1EvjVci2aDNIi8=";
    // Use the secret key below to regenerate the test OPPF data
    protected static final String SECRET_KEY = "ezDKBie96Hnu39gpM2iiIYwfE6cRXzON32K/KbLusYk=";

    // Wrong key that is not trusted
    protected static final String WRONG_PUBLIC_KEY = "3z1cAHQRAkeY+NJg3/st5DGUdEXICcvRWeMT4y5l0CQ=";

    // An OPPF that is valid, unexpired and has a good signature
    protected static final String TEST_GOOD_OPPF = "{\n" +
        "    \"license\": {\n" +
        "        \"expires\": \"2099-12-31\",\n" +
        "        \"count\": 100,\n" +
        "        \"id\": \"DUMMY-00000001\"\n" +
        "    },\n" +
        "    \"blob\": {\n" +
        "        \"uploadUrl\": \"https://blob.threemaonprem.initrode.com/blob/upload\",\n" +
        "        \"downloadUrl\": \"https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}\",\n" +
        "        \"doneUrl\": \"https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done\"\n" +
        "    },\n" +
        "    \"web\": {\"url\": \"https://web.threemaonprem.initrode.com/\"},\n" +
        "    \"chat\": {\n" +
        "        \"hostname\": \"chat.threemaonprem.initrode.com\",\n" +
        "        \"publicKey\": \"r9utIHN9ngo21q9OlZcotsQu1f2HwAW2Wi+u6Psp4Wc=\",\n" +
        "        \"ports\": [\n" +
        "            5222,\n" +
        "            443\n" +
        "        ]\n" +
        "    },\n" +
        "    \"work\": {\"url\": \"https://work.threemaonprem.initrode.com/\"},\n" +
        "    \"signatureKey\": \"jae1lgwR3W7YyKiGQlsbdqObG13FR1EvjVci2aDNIi8=\",\n" +
        "    \"safe\": {\"url\": \"https://safe.threemaonprem.initrode.com/\"},\n" +
        "    \"refresh\": 86400,\n" +
        "    \"avatar\": {\"url\": \"https://avatar.threemaonprem.initrode.com/\"},\n" +
        "    \"mediator\": {\n" +
        "        \"blob\": {\n" +
        "            \"uploadUrl\": \"https://mediator.threemaonprem.initrode.com/blob/upload\",\n" +
        "            \"downloadUrl\": \"https://mediator.threemaonprem.initrode.com/blob/{blobId}\",\n" +
        "            \"doneUrl\": \"https://mediator.threemaonprem.initrode.com/blob/{blobId}/done\"\n" +
        "        },\n" +
        "        \"url\": \"https://mediator.threemaonprem.initrode.com/\"\n" +
        "    },\n" +
        "    \"version\": \"1.0\",\n" +
        "    \"directory\": {\"url\": \"https://dir.threemaonprem.initrode.com/directory\"}\n" +
        "}\n" +
        "M7wDC1VmFZZ72vcKlKdoywN7ok7ZAfsUP1QQ6tIUxRUbj83kxS+AFpglpsYOgHNsLUi8wtXvIbADEnYEwJD4DQ==";

    // An OPPF that is expired but has a good signature
    protected static final String TEST_EXPIRED_OPPF = "{\n" +
        "    \"license\": {\n" +
        "        \"expires\": \"2020-12-31\",\n" +
        "        \"count\": 100,\n" +
        "        \"id\": \"DUMMY-00000001\"\n" +
        "    },\n" +
        "    \"blob\": {\n" +
        "        \"uploadUrl\": \"https://blob.threemaonprem.initrode.com/blob/upload\",\n" +
        "        \"downloadUrl\": \"https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}\",\n" +
        "        \"doneUrl\": \"https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done\"\n" +
        "    },\n" +
        "    \"web\": {\"url\": \"https://web.threemaonprem.initrode.com/\"},\n" +
        "    \"chat\": {\n" +
        "        \"hostname\": \"chat.threemaonprem.initrode.com\",\n" +
        "        \"publicKey\": \"r9utIHN9ngo21q9OlZcotsQu1f2HwAW2Wi+u6Psp4Wc=\",\n" +
        "        \"ports\": [\n" +
        "            5222,\n" +
        "            443\n" +
        "        ]\n" +
        "    },\n" +
        "    \"work\": {\"url\": \"https://work.threemaonprem.initrode.com/\"},\n" +
        "    \"signatureKey\": \"jae1lgwR3W7YyKiGQlsbdqObG13FR1EvjVci2aDNIi8=\",\n" +
        "    \"safe\": {\"url\": \"https://safe.threemaonprem.initrode.com/\"},\n" +
        "    \"refresh\": 86400,\n" +
        "    \"avatar\": {\"url\": \"https://avatar.threemaonprem.initrode.com/\"},\n" +
        "    \"mediator\": {\n" +
        "        \"blob\": {\n" +
        "            \"uploadUrl\": \"https://mediator.threemaonprem.initrode.com/blob/upload\",\n" +
        "            \"downloadUrl\": \"https://mediator.threemaonprem.initrode.com/blob/{blobId}\",\n" +
        "            \"doneUrl\": \"https://mediator.threemaonprem.initrode.com/blob/{blobId}/done\"\n" +
        "        },\n" +
        "        \"url\": \"https://mediator.threemaonprem.initrode.com/\"\n" +
        "    },\n" +
        "    \"version\": \"1.0\",\n" +
        "    \"directory\": {\"url\": \"https://dir.threemaonprem.initrode.com/directory\"}\n" +
        "}\n" +
        "QgT5dACFuMyezF3feHIIwxWwnT88yUAmi9eJ+3Ao/GoKMDmQkBMrfUX6zAvLtE8WsS8SRE0c/MDsK2+kCwA0BA==";
}
