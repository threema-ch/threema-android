/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import ch.threema.base.ThreemaException;

public class OnPremConfigVerifierTest {
    @Test
    public void testVerifyGood() throws IOException, InvalidAlgorithmParameterException, ThreemaException, NoSuchAlgorithmException, JSONException, SignatureException, InvalidKeyException {
        OnPremConfigVerifier verifier = new OnPremConfigVerifier(new String[]{OnPremConfigTestData.PUBLIC_KEY});
        JSONObject obj = verifier.verify(OnPremConfigTestData.TEST_GOOD_OPPF);
        Assert.assertEquals("1.0", obj.getString("version"));
    }

    @Test(expected = ThreemaException.class)
    public void testVerifyBad() throws IOException, InvalidAlgorithmParameterException, ThreemaException, NoSuchAlgorithmException, JSONException, SignatureException, InvalidKeyException {
        OnPremConfigVerifier verifier = new OnPremConfigVerifier(new String[]{OnPremConfigTestData.PUBLIC_KEY});
        // Create a damaged signature by flipping a character
        String badOppf = OnPremConfigTestData.TEST_GOOD_OPPF.replace("initrode", "injtrode");
        JSONObject obj = verifier.verify(badOppf);
    }

    @Test(expected = ThreemaException.class)
    public void testVerifyWrongKey() throws IOException, InvalidAlgorithmParameterException, ThreemaException, NoSuchAlgorithmException, JSONException, SignatureException, InvalidKeyException {
        OnPremConfigVerifier verifier = new OnPremConfigVerifier(new String[]{OnPremConfigTestData.WRONG_PUBLIC_KEY});
        JSONObject obj = verifier.verify(OnPremConfigTestData.TEST_GOOD_OPPF);
    }
}
