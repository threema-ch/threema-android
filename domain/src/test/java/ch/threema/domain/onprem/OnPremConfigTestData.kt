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

import ch.threema.testhelpers.loadResource

object OnPremConfigTestData {
    /**
     * The public key used to verify the OPPF signature.
     *
     * The corresponding secret key is ezDKBie96Hnu39gpM2iiIYwfE6cRXzON32K/KbLusYk=
     * It can be used to regenerate the signature whenever the data in [goodOppf] is modified.
     */
    const val PUBLIC_KEY: String = "jae1lgwR3W7YyKiGQlsbdqObG13FR1EvjVci2aDNIi8="

    /**
     * Wrong key that is not trusted
     */
    const val WRONG_PUBLIC_KEY: String = "3z1cAHQRAkeY+NJg3/st5DGUdEXICcvRWeMT4y5l0CQ="

    /**
     * An OPPF that is valid, unexpired and has a good signature
     */
    val goodOppf
        get() = loadResource("oppf/good_oppf")

    /**
     * An OPPF that is valid, unexpired and has a good signature, but contains only the minimally required fields
     */
    val minimalOppf
        get() = loadResource("oppf/minimal_oppf")

    /**
     * An OPPF with an invalid signature
     */
    val badOppf
        get() = goodOppf.replace("initrode", "injtrode")
}
