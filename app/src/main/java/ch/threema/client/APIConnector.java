/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

import android.annotation.SuppressLint;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.client.work.WorkContact;
import ch.threema.client.work.WorkData;
import ch.threema.client.work.WorkDirectory;
import ch.threema.client.work.WorkDirectoryCategory;
import ch.threema.client.work.WorkDirectoryContact;
import ch.threema.client.work.WorkDirectoryFilter;

/**
 * Fetches data and executes commands on the Threema API (such as creating a new
 * identity, fetching public keys for a given identity, linking e-mail addresses
 * and mobile phone numbers, etc.).
 * <p>
 * All calls run synchronously; if necessary the caller should dispatch a separate thread.
 */
@SuppressWarnings("DuplicateThrows")
public class APIConnector {

	private static final Logger logger = LoggerFactory.getLogger(APIConnector.class);

	/* HMAC-SHA256 keys for contact matching */
	private static final byte[] EMAIL_HMAC_KEY = new byte[]{(byte) 0x30, (byte) 0xa5, (byte) 0x50, (byte) 0x0f, (byte) 0xed, (byte) 0x97, (byte) 0x01, (byte) 0xfa, (byte) 0x6d, (byte) 0xef, (byte) 0xdb, (byte) 0x61, (byte) 0x08, (byte) 0x41, (byte) 0x90, (byte) 0x0f, (byte) 0xeb, (byte) 0xb8, (byte) 0xe4, (byte) 0x30, (byte) 0x88, (byte) 0x1f, (byte) 0x7a, (byte) 0xd8, (byte) 0x16, (byte) 0x82, (byte) 0x62, (byte) 0x64, (byte) 0xec, (byte) 0x09, (byte) 0xba, (byte) 0xd7};
	private static final byte[] MOBILENO_HMAC_KEY = new byte[]{(byte) 0x85, (byte) 0xad, (byte) 0xf8, (byte) 0x22, (byte) 0x69, (byte) 0x53, (byte) 0xf3, (byte) 0xd9, (byte) 0x6c, (byte) 0xfd, (byte) 0x5d, (byte) 0x09, (byte) 0xbf, (byte) 0x29, (byte) 0x55, (byte) 0x5e, (byte) 0xb9, (byte) 0x55, (byte) 0xfc, (byte) 0xd8, (byte) 0xaa, (byte) 0x5e, (byte) 0xc4, (byte) 0xf9, (byte) 0xfc, (byte) 0xd8, (byte) 0x69, (byte) 0xe2, (byte) 0x58, (byte) 0x37, (byte) 0x07, (byte) 0x23};

	private static final int DEFAULT_MATCH_CHECK_INTERVAL = 86400;

	private final @NonNull SSLSocketFactoryFactory sslSocketFactoryFactory;

	private final SecureRandom random;
	private final boolean isWork;

	private int matchCheckInterval = DEFAULT_MATCH_CHECK_INTERVAL;

	private Version version;
	private String language;
	private String serverUrl, workServerUrl;
	private final boolean sandbox;

	public APIConnector(
		boolean ipv6,
		String directoryServerUrlOverride,
		boolean isWork,
		boolean sandbox,
		@NonNull SSLSocketFactoryFactory sslSocketFactoryFactory
	) {
		this.random = new SecureRandom();
		this.version = new Version();
		this.isWork = isWork;
		this.sandbox = sandbox;
		this.sslSocketFactoryFactory = sslSocketFactoryFactory;

		if (directoryServerUrlOverride != null) {
			this.serverUrl = directoryServerUrlOverride;
		} else {
			this.setServerUrls(ipv6);
		}
	}

	public APIConnector(
		boolean ipv6,
		boolean isWork,
		boolean sandbox,
		@NonNull SSLSocketFactoryFactory sslSocketFactoryFactory
	) {
		this(ipv6, null, isWork, sandbox, sslSocketFactoryFactory);
	}

	/**
	 * Create a new identity and store it in the given identity store.
	 *
	 * @param identityStore the store for the new identity
	 * @throws Exception
	 */
	public void createIdentity(IdentityStoreInterface identityStore) throws Exception {
		createIdentity(identityStore, null);
	}

	/**
	 * Create a new identity and store it in the given identity store.
	 *
	 * @param identityStore the store for the new identity
	 * @param seed          additional random data to be used for key generation
	 * @throws Exception
	 */
	public void createIdentity(IdentityStoreInterface identityStore, byte[] seed) throws Exception {
		createIdentity(identityStore, seed, null, null, null, null, null, null);
	}

	/**
	 * Create a new identity and store it in the given identity store.
	 *
	 * @param identityStore   the store for the new identity
	 * @param seed            additional random data to be used for key generation
	 * @param lvlResponseData response data from Google LVL
	 * @param lvlSignature    signature from Google LVL
	 * @param deviceId        unique device ID
	 * @param licenseKey      license key for direct distribution (or null)
	 * @param licenseUsername license username for work (or null)
	 * @param licensePassword license password for work (or null)
	 * @throws Exception
	 */
	public void createIdentity(IdentityStoreInterface identityStore, byte[] seed,
							   String lvlResponseData, String lvlSignature,
							   String deviceId, String licenseKey,
							   String licenseUsername, String licensePassword) throws Exception {
		String url = serverUrl + "identity/create";

		/* generate new key pair and store */
		logger.debug("Generating new key pair");
		byte[] publicKey = new byte[NaCl.PUBLICKEYBYTES];
		byte[] privateKey = new byte[NaCl.SECRETKEYBYTES];

		/* seed available? */
		byte[] hashedSeed = null;
		if (seed != null) {
			/* hash the seed to ensure it is unbiased and has the right length */
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			hashedSeed = md.digest(seed);
		}

		NaCl.genkeypair(publicKey, privateKey, hashedSeed);

		/* phase 1: send public key to server */
		logger.debug("Sending public key to server");
		JSONObject p1Body = new JSONObject();
		p1Body.put("publicKey", Base64.encodeBytes(publicKey));

		String p1ResultString = doPost(url, p1Body.toString());
		JSONObject p1Result = new JSONObject(p1ResultString);

		String tokenString = p1Result.getString("token");

		byte[] token = Base64.decode(tokenString);
		byte[] tokenRespKeyPub = Base64.decode(p1Result.getString("tokenRespKeyPub"));

		logger.debug("Got token from server; sending response");

		/* phase 2: encrypt token and send response to server */
		String nonceStr = "createIdentity response.";
		NaCl nacl = new NaCl(privateKey, tokenRespKeyPub);
		byte[] clientResponse = nacl.encrypt(token, nonceStr.getBytes());

		JSONObject p2Body = new JSONObject();
		p2Body.put("publicKey", Base64.encodeBytes(publicKey));
		p2Body.put("token", tokenString);
		p2Body.put("response", Base64.encodeBytes(clientResponse));

		if (lvlResponseData != null && lvlSignature != null) {
			p2Body.put("lvlResponseData", lvlResponseData);
			p2Body.put("lvlSignature", lvlSignature);
		}

		if (deviceId != null) {
			p2Body.put("deviceId", deviceId);
		}

		if (licenseKey != null) {
			p2Body.put("licenseKey", licenseKey);
		}

		if (licenseUsername != null) {
			p2Body.put("licenseUsername", licenseUsername);
		}

		if (licensePassword != null) {
			p2Body.put("licensePassword", licensePassword);
		}

		String p2ResultString = doPost(url, p2Body.toString());
		JSONObject p2Result = new JSONObject(p2ResultString);

		boolean success = p2Result.getBoolean("success");
		if (!success)
			throw new ThreemaException("TA001: " + p2Result.getString("error"));    /* Create identity phase 2 not successful; error: */

		String identity = p2Result.getString("identity");
		String serverGroup = p2Result.getString("serverGroup");

		logger.info("New identity: {}, server group: {}", identity, serverGroup);

		identityStore.storeIdentity(identity, serverGroup, publicKey, privateKey);
	}

	/**
	 * Fetch identity-related information (public key) for
	 * a given identity.
	 *
	 * @param identity the desired identity
	 * @return information related to identity
	 * @throws FileNotFoundException if identity not found
	 * @throws Exception             on network error
	 */
	public FetchIdentityResult fetchIdentity(String identity) throws FileNotFoundException, Exception {
		String responseStr = doGet(serverUrl + "identity/" + identity);
		JSONObject jsonResponse = new JSONObject(responseStr);

		FetchIdentityResult result = new FetchIdentityResult();
		result.publicKey = Base64.decode(jsonResponse.getString("publicKey"));
		result.featureLevel = jsonResponse.optInt("featureLevel");
		result.featureMask = jsonResponse.optInt("featureMask");
		result.identity = jsonResponse.getString("identity");
		result.state = jsonResponse.optInt("state");
		result.type = jsonResponse.optInt("type");
		return result;
	}


	/**
	 * Fetch identity-related information for given identities.
	 *
	 * @param identities the desired identities
	 * @return array list of information related to identity
	 * @throws FileNotFoundException if identity not found
	 * @throws Exception             on network error
	 */
	public ArrayList<FetchIdentityResult> fetchIdentities(ArrayList<String> identities) throws FileNotFoundException, Exception {
		if (identities == null || identities.size() < 1) {
			throw new ThreemaException("empty identities array");
		}

		JSONObject postObject = new JSONObject();
		postObject.put("identities", new JSONArray(identities));
		String postResponse = doPost(serverUrl + "identity/fetch_bulk", postObject.toString());

		if (postResponse == null) {
			throw new ThreemaException("no valid response or network error");
		}

		JSONObject resultObject = new JSONObject(postResponse);
		JSONArray resultArray = resultObject.getJSONArray("identities");

		ArrayList<FetchIdentityResult> fetchIdentityResults = new ArrayList<>();
		for (int i=0; i < resultArray.length(); i++) {
			JSONObject jsonResponse = resultArray.getJSONObject(i);
			FetchIdentityResult fetchIdentityResult = new FetchIdentityResult();
			fetchIdentityResult.publicKey = Base64.decode(jsonResponse.getString("publicKey"));
			fetchIdentityResult.featureLevel = jsonResponse.optInt("featureLevel");
			fetchIdentityResult.featureMask = jsonResponse.optInt("featureMask");
			fetchIdentityResult.identity = jsonResponse.getString("identity");
			fetchIdentityResult.state = jsonResponse.optInt("state");
			fetchIdentityResult.type = jsonResponse.optInt("type");

			fetchIdentityResults.add(fetchIdentityResult);
		}
		return fetchIdentityResults;
	}

	/**
	 * Fetch private identity-related information (server group, linked e-mail/mobile number).
	 *
	 * @param identityStore the identity store to use
	 * @return fetched private identity information
	 */
	public FetchIdentityPrivateResult fetchIdentityPrivate(IdentityStoreInterface identityStore) throws Exception {

		String url = serverUrl + "identity/fetch_priv";

		/* phase 1: send identity */
		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());

		logger.debug("Fetch identity private phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Fetch identity private phase 1: response from server: {}", p1Result);

		if (p1Result.has("success") && !p1Result.getBoolean("success")) {
			throw new ThreemaException(p1Result.getString("error"));
		}

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Fetch identity private: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Fetch identity private: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success")) {
			throw new ThreemaException(p2Result.getString("error"));
		}

		FetchIdentityPrivateResult result = new FetchIdentityPrivateResult();
		result.serverGroup = p2Result.getString("serverGroup");
		if (p2Result.has("email"))
			result.email = p2Result.getString("email");
		if (p2Result.has("mobileNo"))
			result.mobileNo = p2Result.getString("mobileNo");
		return result;
	}
	/**
	 * Link an e-mail address with the identity from the given store. The user gets a verification
	 * e-mail with a link. {@link #linkEmailCheckStatus(String, IdentityStoreInterface)} should be called
	 * to check whether the user has already confirmed.
	 * <p>
	 * To unlink, pass an empty string as the e-mail address. In that case, checking status is not
	 * necessary as the unlink operation does not need e-mail verification.
	 *
	 * @param email         e-mail address to be linked, or empty string to unlink
	 * @param language      language for confirmation e-mail, ISO-639-1 (e.g. "de", "en", "fr")
	 * @param identityStore identity store for authentication of request
	 * @return true if e-mail address is accepted for verification, false if already linked
	 * @throws LinkEmailException if the server reports an error (should be displayed to the user verbatim)
	 * @throws Exception          if a network error occurs
	 */
	public boolean linkEmail(String email, String language, IdentityStoreInterface identityStore) throws LinkEmailException, Exception {

		String url = serverUrl + "identity/link_email";

		/* phase 1: send identity and e-mail */
		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		request.put("email", email);
		request.put("lang", language);

		logger.debug("Link e-mail phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Link e-mail phase 1: response from server: {}", p1Result);

		if (!p1Result.has("linked"))
			throw new LinkEmailException(p1Result.getString("error"));

		if (p1Result.getBoolean("linked"))
			return false;    /* already linked */

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Link e-mail phase 2: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Link e-mail phase 2: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success"))
			throw new LinkEmailException(p2Result.getString("error"));

		return true;
	}

	/**
	 * Check whether a given e-mail address is already linked to the identity (i.e. the user
	 * has confirmed the verification mail).
	 *
	 * @param email         e-mail address to be linked
	 * @param identityStore identity store for authentication of request
	 * @return e-mail address linked true/false
	 * @throws Exception if a network error occurs
	 */
	public boolean linkEmailCheckStatus(String email, IdentityStoreInterface identityStore) throws Exception {
		String url = serverUrl + "identity/link_email";

		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		request.put("email", email);

		logger.debug("Link e-mail check: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Link e-mail check: response from server: {}", p1Result);

		return p1Result.getBoolean("linked");
	}

	/**
	 * Link a mobile phone number with the identity from the given store. The user gets a verification code via
	 * SMS; this code should be passed to {@link #linkMobileNoVerify(String, String)} along with the verification ID
	 * returned by this method to complete the operation.
	 * <p>
	 * To unlink, pass an empty string as the mobile number.
	 *
	 * @param mobileNo      mobile phone number in E.164 format without + (e.g. 41791234567)
	 * @param language      language for SMS text, ISO-639-1 (e.g. "de", "en", "fr")
	 * @param identityStore identity store for authentication of request
	 * @return verification ID that should be passed to {@link #linkMobileNoVerify(String, String)}, or null if verification is already complete
	 * @throws LinkMobileNoException if the server reports an error (should be displayed to the user verbatim)
	 * @throws Exception             if a network error occurs
	 */
	public String linkMobileNo(String mobileNo, String language, IdentityStoreInterface identityStore) throws LinkMobileNoException, Exception {
		return this.linkMobileNo(mobileNo, language, identityStore, null);
	}

	/**
	 * Link a mobile phone number with the identity from the given store. The user gets a verification code via
	 * SMS; this code should be passed to {@link #linkMobileNoVerify(String, String)} along with the verification ID
	 * returned by this method to complete the operation.
	 * <p>
	 * To unlink, pass an empty string as the mobile number.
	 *
	 * @param mobileNo      mobile phone number in E.164 format without + (e.g. 41791234567)
	 * @param language      language for SMS text, ISO-639-1 (e.g. "de", "en", "fr")
	 * @param identityStore identity store for authentication of request
	 * @param urlScheme     optional parameter (url schema of the verification link)
	 * @return verification ID that should be passed to {@link #linkMobileNoVerify(String, String)}, or null if verification is already complete
	 * @throws LinkMobileNoException if the server reports an error (should be displayed to the user verbatim)
	 * @throws Exception             if a network error occurs
	 */
	public String linkMobileNo(String mobileNo, String language, IdentityStoreInterface identityStore, String urlScheme) throws LinkMobileNoException, Exception {
		String url = serverUrl + "identity/link_mobileno";

		/* phase 1: send identity and mobile no */
		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		request.put("mobileNo", mobileNo);
		request.put("lang", language);
		request.put("httpsUrl", true);
		if (urlScheme != null && urlScheme.length() > 0) {
			request.put("urlScheme", true);
		}

		logger.debug("Link mobile number phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Link mobile number phase 1: response from server: {}", p1Result);

		if (!p1Result.has("linked"))
			throw new LinkMobileNoException(p1Result.getString("error"));

		if (p1Result.getBoolean("linked"))
			return null;    /* already linked */

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Link mobile number phase 2: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Link mobile number phase 2: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success"))
			throw new LinkMobileNoException(p2Result.getString("error"));

		if (mobileNo.length() > 0)
			return p2Result.getString("verificationId");
		else
			return null;
	}

	/**
	 * Complete verification of mobile number link.
	 *
	 * @param verificationId the verification ID returned by {@link #linkMobileNo(String, String, IdentityStoreInterface)}
	 * @param code           the SMS code (usually 6 digits)
	 * @throws LinkMobileNoException if the server reports an error, e.g. wrong code or too many attempts (should be displayed to the user verbatim)
	 * @throws Exception             if a network error occurs
	 */
	public void linkMobileNoVerify(String verificationId, String code) throws LinkMobileNoException, Exception {
		String url = serverUrl + "identity/link_mobileno";

		JSONObject request = new JSONObject();
		request.put("verificationId", verificationId);
		request.put("code", code);

		JSONObject result = new JSONObject(doPost(url, request.toString()));

		if (!result.getBoolean("success"))
			throw new LinkMobileNoException(result.getString("error"));
	}

	/**
	 * Trigger a phone call for the given verification ID. This should only be done if the SMS doesn't arrive
	 * in a normal amount of time (e.g. 10 minutes). The verification code will be read to the user twice,
	 * and {@link #linkMobileNoVerify(String, String)} should then be called with the code.
	 *
	 * @param verificationId verification ID returned from {@link #linkMobileNo(String, String, IdentityStoreInterface)}
	 * @throws LinkMobileNoException if the server reports an error, e.g. unable to call the destination, already called etc. (should be displayed to the user verbatim)
	 * @throws Exception             if a network error occurs
	 */
	public void linkMobileNoCall(String verificationId) throws LinkMobileNoException, Exception {
		String url = serverUrl + "identity/link_mobileno_call";

		JSONObject request = new JSONObject();
		request.put("verificationId", verificationId);

		JSONObject result = new JSONObject(doPost(url, request.toString()));

		if (!result.getBoolean("success"))
			throw new LinkMobileNoException(result.getString("error"));
	}

	/**
	 * Find identities that have been linked with the given e-mail addresses and/or mobile phone numbers.
	 * The mobile phone numbers can be provided in national or international format, as they will be automatically
	 * passed through libphonenumber (which also takes care of spaces, brackets etc.).
	 * <p>
	 * The server also returns its desired check interval to the {@code APIConnector} object during this call.
	 * The caller should use {@link #getMatchCheckInterval()} to determine the earliest time for the next call
	 * after this call. This is important so that the server can request longer intervals from its clients during
	 * periods of heavy traffic or temporary capacity problems.
	 *
	 * @param emails          map of e-mail addresses (key = e-mail, value = arbitrary object for reference that is returned with any found identities)
	 * @param mobileNos       map of phone numbers (key = phone number, value = arbitrary object for reference that is returned with any found identities)
	 * @param userCountry     the user's home country (for correct interpretation of national phone numbers), ISO 3166-1, e.g. "CH" (or null to disable normalization)
	 * @param includeInactive if true, inactive IDs will be included in the results also
	 * @param identityStore   identity store to use for obtaining match token
	 * @param matchTokenStore for storing match token for reuse (may be null)
	 * @return map of found identities (key = identity). The value objects from the {@code emails} and {@code mobileNos} parameters
	 * will be returned in {@code refObject}.
	 */
	@SuppressLint("DefaultLocale")
	public Map<String, MatchIdentityResult> matchIdentities(Map<String, ?> emails, Map<String, ?> mobileNos, String userCountry, boolean includeInactive, IdentityStoreInterface identityStore, MatchTokenStoreInterface matchTokenStore) throws Exception {
		/* normalize and hash e-mail addresses */
		Map<String, Object> emailHashes = new HashMap<>();

		Mac emailMac = Mac.getInstance("HmacSHA256");
		emailMac.init(new SecretKeySpec(EMAIL_HMAC_KEY, "HmacSHA256"));

		for (Map.Entry<String, ?> entry : emails.entrySet()) {
			String normalizedEmail = entry.getKey().toLowerCase().trim();
			byte[] emailHash = emailMac.doFinal(normalizedEmail.getBytes(StandardCharsets.US_ASCII));
			emailHashes.put(Base64.encodeBytes(emailHash), entry.getValue());

			/* Gmail address? If so, hash with the other domain as well */
			String normalizedEmailAlt = null;
			if (normalizedEmail.endsWith("@gmail.com"))
				normalizedEmailAlt = normalizedEmail.replace("@gmail.com", "@googlemail.com");
			else if (normalizedEmail.endsWith("@googlemail.com"))
				normalizedEmailAlt = normalizedEmail.replace("@googlemail.com", "@gmail.com");

			if (normalizedEmailAlt != null) {
				byte[] emailHashAlt = emailMac.doFinal(normalizedEmailAlt.getBytes(StandardCharsets.US_ASCII));
				emailHashes.put(Base64.encodeBytes(emailHashAlt), entry.getValue());
			}
		}

		/* normalize and hash phone numbers */
		Map<String, Object> mobileNoHashes = new HashMap<>();

		Mac mobileNoMac = Mac.getInstance("HmacSHA256");
		mobileNoMac.init(new SecretKeySpec(MOBILENO_HMAC_KEY, "HmacSHA256"));
		PhoneNumberUtil phoneNumberUtil = null;

		if (userCountry != null)
			phoneNumberUtil = PhoneNumberUtil.getInstance();

		for (Map.Entry<String, ?> entry : mobileNos.entrySet()) {
			try {
				String normalizedMobileNo;
				if (phoneNumberUtil != null) {
					Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(entry.getKey(), userCountry);
					String normalizedMobileNoWithPlus = phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
					normalizedMobileNo = normalizedMobileNoWithPlus.replace("+", "");
				} else {
					normalizedMobileNo = entry.getKey().replaceAll("[^0-9]", "");
				}

				byte[] mobileNoHash = mobileNoMac.doFinal(normalizedMobileNo.getBytes("US-ASCII"));
				mobileNoHashes.put(Base64.encodeBytes(mobileNoHash), entry.getValue());
			} catch (NumberParseException e) {
				/* skip/ignore this number */
				logger.debug("Failed to parse phone number {}: {}", entry.getKey(), e.getMessage());
			}
		}

		return matchIdentitiesHashed(emailHashes, mobileNoHashes, includeInactive, identityStore, matchTokenStore);
	}

	public Map<String, MatchIdentityResult> matchIdentitiesHashed(Map<String, ?> emailHashes, Map<String, ?> mobileNoHashes, boolean includeInactive, IdentityStoreInterface identityStore, MatchTokenStoreInterface matchTokenStore) throws Exception {
		String matchToken = obtainMatchToken(identityStore, matchTokenStore, false);

		try {
			return matchIdentitiesHashedToken(emailHashes, mobileNoHashes, includeInactive, matchToken);
		} catch (Exception e) {
			// Match token may be invalid/expired, refresh and try again
			logger.debug("Match failed", e);
			matchToken = obtainMatchToken(identityStore, matchTokenStore, true);
			return matchIdentitiesHashedToken(emailHashes, mobileNoHashes, includeInactive, matchToken);
		}
	}

	private Map<String, MatchIdentityResult> matchIdentitiesHashedToken(Map<String, ?> emailHashes, Map<String, ?> mobileNoHashes, boolean includeInactive, String matchToken) throws Exception {
		String url = serverUrl + "identity/match";

		/* send hashes to server */
		JSONObject request = new JSONObject();
		if (matchToken != null) {
			request.put("matchToken", matchToken);
		}
		request.put("emailHashes", new JSONArray(emailHashes.keySet()));
		request.put("mobileNoHashes", new JSONArray(mobileNoHashes.keySet()));
		if (includeInactive)
			request.put("includeInactive", Boolean.TRUE);

		logger.debug(String.format("Match identities: sending to server: %s", request.toString()));

		JSONObject result = new JSONObject(doPost(url, request.toString()));
		logger.debug(String.format("Match identities: response from server: %s", result.toString()));

		matchCheckInterval = result.getInt("checkInterval");
		logger.debug(String.format("Server requested check interval of %d seconds", matchCheckInterval));

		JSONArray identities = result.getJSONArray("identities");

		Map<String, MatchIdentityResult> returnMap = new HashMap<>(identities.length());

		for (int i = 0; i < identities.length(); i++) {
			JSONObject identity = identities.getJSONObject(i);

			MatchIdentityResult resultId = new MatchIdentityResult();
			resultId.publicKey = Base64.decode(identity.getString("publicKey"));
			if (identity.has("emailHash")) {
				resultId.emailHash = Base64.decode(identity.getString("emailHash"));
				resultId.refObjectEmail = emailHashes.get(identity.getString("emailHash"));
			}
			if (identity.has("mobileNoHash")) {
				resultId.mobileNoHash = Base64.decode(identity.getString("mobileNoHash"));
				resultId.refObjectMobileNo = mobileNoHashes.get(identity.getString("mobileNoHash"));
			}

			returnMap.put(identity.getString("identity"), resultId);
		}

		return returnMap;
	}

	private String obtainMatchToken(IdentityStoreInterface identityStore, MatchTokenStoreInterface matchTokenStore, boolean forceRefresh) throws Exception {
		if (identityStore == null || identityStore.getIdentity() == null || identityStore.getIdentity().length() == 0) {
			return null;
		}

		// Cached match token?
		String matchToken = null;
		if (matchTokenStore != null) {
			matchToken = matchTokenStore.getMatchToken();
		}
		if (matchToken != null && !forceRefresh) {
			return matchToken;
		}

		String url = serverUrl + "identity/match_token";

		/* phase 1: send identity */
		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());

		logger.debug("Fetch match token phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Fetch match token phase 1: response from server: {}", p1Result);

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Fetch match token: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Fetch match token: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success"))
			throw new ThreemaException(p2Result.getString("error"));

		matchToken = p2Result.getString("matchToken");
		if (matchTokenStore != null) {
			matchTokenStore.storeMatchToken(matchToken);
		}

		return matchToken;
	}

	/**
	 * Set the group chat flag for the identity in the given store.
	 *
	 * @param featureBuilder feature mask builder of the current identity
	 * @param identityStore  identity store for authentication of request
	 * @throws LinkMobileNoException if the server reports an error (should be displayed to the user verbatim)
	 * @throws Exception             if a network error occurs
	 */
	public void setFeatureMask(ThreemaFeature.Builder featureBuilder, IdentityStoreInterface identityStore) throws Exception {
		String url = serverUrl + "identity/set_featuremask";

//        /* phase 1: send identity */
		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		request.put("featureMask", featureBuilder.build());

		logger.debug("Set feature mask phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Set feature mask phase 1: response from server: {}", p1Result);

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Set feature mask phase 2: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Set feature mask  phase 2: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success")) {
			throw new ThreemaException(p2Result.getString("error"));
		}
	}

	/**
	 * Fetch the feature masks of the supplied identities
	 *
	 * @param identities list of IDs to be checked
	 * @return list of feature masks (null if a invalid identity was set)
	 * @throws Exception on network error
	 */
	public Integer[] checkFeatureMask(String[] identities) throws Exception {
		String url = serverUrl + "identity/check_featuremask";

		JSONObject request = new JSONObject();

		JSONArray jsonIdentities = new JSONArray();
		for (String identity : identities)
			jsonIdentities.put(identity);
		request.put("identities", jsonIdentities);

		JSONObject result = new JSONObject(doPost(url, request.toString()));

		JSONArray jsonArrayFeatureMasks = result.getJSONArray("featureMasks");

		Integer[] featureMasks = new Integer[jsonArrayFeatureMasks.length()];

		for (int i = 0; i < jsonArrayFeatureMasks.length(); i++) {
			if (jsonArrayFeatureMasks.isNull(i)) {
				featureMasks[i] = null;
			} else {
				featureMasks[i] = jsonArrayFeatureMasks.getInt(i);
			}
		}

		return featureMasks;
	}

	/**
	 * Check the revocation key
	 *
	 * @param identityStore
	 * @return
	 * @throws Exception
	 */
	public CheckRevocationKeyResult checkRevocationKey(IdentityStoreInterface identityStore) throws Exception {
		String url = serverUrl + "identity/check_revocation_key";

		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		logger.debug("checkRevocationKey phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("checkRevocationKey phase 1: response from server: {}", p1Result);

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("checkRevocationKey phase 2: sending to server: {}", request);
		JSONObject result = new JSONObject(doPost(url, request.toString()));
		logger.debug("checkRevocationKey phase 2: response from server: {}", result);

		boolean set = result.getBoolean("revocationKeySet");
		Date lastChanged = null;

		if (set) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
			lastChanged = dateFormat.parse(result.getString("lastChanged"));
		}
		return new CheckRevocationKeyResult(set, lastChanged);
	}

	/**
	 * Set the revocation key for the stored identity
	 *
	 * @param identityStore
	 * @param revocationKey
	 * @return
	 * @throws Exception
	 */
	public SetRevocationKeyResult setRevocationKey(IdentityStoreInterface identityStore, String revocationKey) throws Exception {

		//calculate key
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] sha256 = md.digest(revocationKey.getBytes(StandardCharsets.UTF_8));

		String base64KeyPart = Base64.encodeBytes(Arrays.copyOfRange(sha256, 0, 4));
		String url = serverUrl + "identity/set_revocation_key";

		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		request.put("revocationKey", base64KeyPart);

		logger.debug("setRevocationKey phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("setRevocationKey phase 1: response from server: {}", p1Result);

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("setRevocationKey phase 2: sending to server: {}", request);
		JSONObject result = new JSONObject(doPost(url, request.toString()));
		logger.debug("setRevocationKey phase 2: response from server: {}", result);

		if (result.getBoolean("success")) {
			return new SetRevocationKeyResult(true, null);
		} else {
			return new SetRevocationKeyResult(true, result.getString("error"));
		}
	}

	/**
	 * This call is used to check a list of IDs and determine the status of each ID. The response contains a list of status codes, one for each ID in the same order as in the request.
	 *
	 * @param identities
	 * @return
	 * @throws Exception
	 */
	public CheckIdentityStatesResult checkIdentityStates(String[] identities) throws Exception {
		String url = serverUrl + "identity/check";

		JSONObject request = new JSONObject();

		JSONArray jsonIdentities = new JSONArray();
		for (String identity : identities) {
			jsonIdentities.put(identity);
		}
		request.put("identities", jsonIdentities);

		JSONObject result = new JSONObject(doPost(url, request.toString()));

		int interval = result.getInt("checkInterval");
		JSONArray jsonStates = result.getJSONArray("states");

		int[] states = new int[jsonStates.length()];

		for (int i = 0; i < jsonStates.length(); i++) {
			states[i] = jsonStates.getInt(i);
		}
		JSONArray jsonTypes = result.getJSONArray("types");
		int[] types = new int[jsonTypes.length()];

		for (int i = 0; i < jsonTypes.length(); i++) {
			types[i] = jsonTypes.getInt(i);
		}

		JSONArray jsonFeatureMasks = result.getJSONArray("featureMasks");
		Integer[] featureMasks = new Integer[jsonFeatureMasks.length()];

		for (int i = 0; i < jsonFeatureMasks.length(); i++) {
			if (jsonFeatureMasks.isNull(i)) {
				featureMasks[i] = null;
			} else {
				featureMasks[i] = jsonFeatureMasks.getInt(i);
			}
		}

		return new CheckIdentityStatesResult(states,
			types,
			identities,
			interval,
			featureMasks);
	}

	/**
	 * Obtain temporary TURN server URLs and credentials, e.g. for use with VoIP.
	 *
	 * @param identityStore The identity store to use for authentication
	 * @param type The desired TURN server type (usually "voip").
	 * @return TURN server info
	 * @throws Exception If servers could not be obtained
	 */
	public TurnServerInfo obtainTurnServers(IdentityStoreInterface identityStore, String type) throws Exception {
		if (identityStore == null || identityStore.getIdentity() == null || identityStore.getIdentity().length() == 0) {
			return null;
		}

		String url = serverUrl + "identity/turn_cred";

		/* phase 1: send identity and type */
		JSONObject request = new JSONObject();
		request.put("identity", identityStore.getIdentity());
		request.put("type", type);

		logger.debug("Obtain TURN servers phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Obtain TURN servers phase 1: response from server: {}", p1Result);

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Obtain TURN servers phase 2: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Obtain TURN servers phase 2: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success"))
			throw new ThreemaException(p2Result.getString("error"));

		String[] turnUrls = jsonArrayToStringArray(p2Result.getJSONArray("turnUrls"));
		String[] turnUrlsDualStack = jsonArrayToStringArray(p2Result.getJSONArray("turnUrlsDualStack"));
		String turnUsername = p2Result.getString("turnUsername");
		String turnPassword = p2Result.getString("turnPassword");
		int expiration = p2Result.getInt("expiration");
		Date expirationDate = new Date(new Date().getTime() + expiration*1000);

		return new TurnServerInfo(turnUrls, turnUrlsDualStack, turnUsername, turnPassword, expirationDate);
	}

	private String[] jsonArrayToStringArray(JSONArray jsonArray) throws JSONException {
		String[] stringArray = new String[jsonArray.length()];
		for (int i = 0; i < jsonArray.length(); i++) {
			stringArray[i] = jsonArray.getString(i);
		}
		return stringArray;
	}

	/**
	 * Check a license key for direct distribution.
	 *
	 * This will implicitly check for updates as well.
	 *
	 * @param licenseKey the license key (format: XXXXX-XXXXX where X = A-Z/0-9)
	 * @param deviceId   unique device ID
	 * @return result of license check (success status, error message if success = false)
	 * @throws Exception on network error
	 */
	public CheckLicenseResult checkLicense(
		String licenseKey,
		String deviceId
	) throws Exception {
		JSONObject request = new JSONObject();
		request.put("licenseKey", licenseKey);
		return this.checkLicense(request, deviceId);
	}

	/**
	 * Check a username/password for direct distribution (work only).
	 *
	 * @param username the license username
	 * @param password the license password
	 * @param deviceId unique device ID
	 * @return result of license check (success status, error message if success = false)
	 * @throws Exception on network error
	 */
	public CheckLicenseResult checkLicense(String username, String password, String deviceId) throws Exception {
		JSONObject request = new JSONObject();
		request.put("licenseUsername", username);
		request.put("licensePassword", password);
		return this.checkLicense(request, deviceId);
	}

	/**
	 * Check license for direct distribution
	 *
	 * @param request  prefilled json request object
	 * @param deviceId unique device ID
	 * @return result of license check (success status, error message if success = false)
	 * @throws Exception on network error
	 */
	private CheckLicenseResult checkLicense(JSONObject request, String deviceId) throws Exception {
		String url = serverUrl + "check_license";
		request.put("deviceId", deviceId);
		request.put("version", version.getFullVersion());
		request.put("arch", version.getArchitecture());

		JSONObject result = new JSONObject(doPost(url, request.toString()));

		CheckLicenseResult checkLicenseResult = new CheckLicenseResult();
		if (result.getBoolean("success")) {
			checkLicenseResult.success = true;

			if (result.has("updateMessage"))
				checkLicenseResult.updateMessage = result.getString("updateMessage");

			if (result.has("updateUrl"))
				checkLicenseResult.updateUrl = result.getString("updateUrl");

			if (result.has("logoLightUrl"))
				checkLicenseResult.logoLightUrl = result.getString("logoLightUrl");

			if (result.has("logoDarkUrl"))
				checkLicenseResult.logoDarkUrl = result.getString("logoDarkUrl");

		} else {
			checkLicenseResult.success = false;
			checkLicenseResult.error = result.getString("error");
		}
		return checkLicenseResult;
	}

	/**
	 * Fetch all custom work data from work api
	 *
	 * @param username
	 * @param password
	 * @param identities (list of existing threema id
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 */
	public WorkData fetchWorkData(String username, String password, String[] identities) throws Exception {
		WorkData workData = new WorkData();

		JSONObject request = new JSONObject();
		request.put("username", username);
		request.put("password", password);

		JSONArray identityArray = new JSONArray();
		for (String identity : identities) {
			identityArray.put(identity);
		}
		request.put("contacts", identityArray);

		String data = doPost(
			this.workServerUrl + "fetch2",
			request.toString());

		if (data == null || data.length() == 0) {
			return workData;
		}

		JSONObject jsonResponse = new JSONObject(data);
		if (jsonResponse.has("support") && !jsonResponse.isNull("support")) {
			workData.supportUrl = jsonResponse.getString("support");
		}

		if (jsonResponse.has("logo")) {
			final JSONObject logos = jsonResponse.getJSONObject("logo");
			if (logos.has("dark") && !logos.isNull("dark")) {
				workData.logoDark = logos.getString("dark");
			}
			if (logos.has("light") && !logos.isNull("light")) {
				workData.logoLight = logos.getString("light");
			}
		}

		workData.checkInterval = (jsonResponse.has("checkInterval") ?
			jsonResponse.getInt("checkInterval") : 0);

		if (jsonResponse.has("contacts")) {
			JSONArray contacts = jsonResponse.getJSONArray("contacts");

			for (int n = 0; n < contacts.length(); n++) {
				JSONObject contact = contacts.getJSONObject(n);

				//validate fields
				if (contact.has("id") && contact.has("pk")) {
					workData.workContacts.add(new WorkContact(
						contact.getString("id"),
						Base64.decode(contact.getString("pk")),
						contact.has("first") ? contact.getString("first") : null,
						contact.has("last") ? contact.getString("last") : null
					));
				}
			}
		}

		if (jsonResponse.has("mdm")) {
			JSONObject jsonMDM = jsonResponse.getJSONObject("mdm");
			workData.mdm.override = jsonMDM.optBoolean("override", false);
			if (jsonMDM.has("params")) {
				JSONObject jsonMAMParameters = jsonMDM.getJSONObject("params");
				Iterator<String> keys = jsonMAMParameters.keys();

				while (keys.hasNext()) {
					String currentKey = keys.next();
					workData.mdm.parameters.put(
						currentKey,
						jsonMAMParameters.get(currentKey)
					);
				}

			}
		}

		// Since Release: work-directory
		JSONObject jsonResponseOrganization = jsonResponse.optJSONObject("org");
		if (jsonResponseOrganization != null) {
			workData.organization.name = jsonResponseOrganization.optString("name");
		}

		JSONObject directory = jsonResponse.optJSONObject("directory");
		if (directory != null) {
			workData.directory.enabled = directory.optBoolean("enabled", false);
			JSONObject categories = directory.optJSONObject("cat");
			if (categories != null) {
				Iterator<String> keys = categories.keys();

				while (keys.hasNext()) {
					String categoryId = keys.next();
					workData.directory.categories.add(new WorkDirectoryCategory(
							categoryId,
							categories.getString(categoryId)));
				}
			}

		}
		return workData;
	}
	/**
	 * Search the threema work directory without categories
	 *
	 * @param username
	 * @param password
	 * @param filter
	 * @return Can be null
	 * @throws IOException
	 * @throws JSONException
	 */
	public WorkDirectory fetchWorkDirectory(String username,
											String password,
											IdentityStoreInterface identityStore,
											WorkDirectoryFilter filter) throws Exception {

		JSONObject request = new JSONObject();
		request.put("username", username);
		request.put("password", password);
		request.put("identity", identityStore.getIdentity());
		request.put("query", filter.getQuery());


		// Filter category
		if (filter.getCategories() != null && filter.getCategories().size() > 0) {
			JSONArray jsonCategories = new JSONArray();
			for (WorkDirectoryCategory category: filter.getCategories()) {
				jsonCategories.put(category.id);
			}
			request.put("categories", jsonCategories);
		}

		// Sorting
		JSONObject jsonSort = new JSONObject();

		jsonSort.put("asc", filter.isSortAscending());
		switch (filter.getSortBy()) {
			case WorkDirectoryFilter.SORT_BY_LAST_NAME:
				jsonSort.put("by", "lastName");
				break;
			default:
				jsonSort.put("by", "firstName");
				break;
		}

		request.put("sort", jsonSort);

		// Paging
		request.put("page", filter.getPage());

		String data = doPost(
			workServerUrl + "directory",
			request.toString());

		// Verify request
		if (data == null
			|| data.length() == 0) {
			// Return null
			return null;
		}

		JSONObject jsonResponse = new JSONObject(data);


		if (jsonResponse.has("contacts") && !jsonResponse.isNull("contacts")) {

			// Verify content
			JSONArray contacts = jsonResponse.getJSONArray("contacts");

			int total = contacts.length();
			int pageSize = total;
			WorkDirectoryFilter filterNext = null;
			WorkDirectoryFilter filterPrevious = null;

			if (jsonResponse.has("paging") && !jsonResponse.isNull("paging")) {
				JSONObject paging = jsonResponse.getJSONObject("paging");

				pageSize = paging.optInt("size", pageSize);
				total = paging.optInt("total", total);
				if (paging.has("next")) {
					// Next filter
					filterNext = filter.copy()
						.page(jsonResponse.optInt("next", filter.getPage() + 1));
				}
				if (paging.has("prev")) {
					// Next filter
					filterPrevious = filter.copy()
						.page(jsonResponse.optInt("prev", filter.getPage() - 1));
				}
			}

			WorkDirectory workDirectory = new WorkDirectory(
				total,
				pageSize,
				filter,
				filterNext,
				filterPrevious
			);

			for (int n = 0; n < contacts.length(); n++) {
				JSONObject contact = contacts.getJSONObject(n);

				//validate fields
				if (contact.has("id") && contact.has("pk")) {
					WorkDirectoryContact directoryContact = new WorkDirectoryContact(
						contact.getString("id"),
						Base64.decode(contact.getString("pk")),
						contact.has("first") ? contact.optString("first") : null,
						contact.has("last") ? contact.optString("last") : null,
						contact.has("csi") ? contact.optString("csi") : null
					);
					JSONObject jsonResponseOrganization = contact.optJSONObject("org");
					if (jsonResponseOrganization != null) {
						directoryContact.organization.name = jsonResponseOrganization.optString("name");
					}

					JSONArray categoryArray = contact.optJSONArray("cat");
					if (categoryArray != null) {
						for (int cN = 0; cN < categoryArray.length(); cN++) {
							directoryContact.categoryIds.add(categoryArray.getString(cN));
						}
					}

					workDirectory.workContacts.add(directoryContact);
				}
			}

			return workDirectory;
		}

		// Invalid request
		return null;

	}

	/**
	 * Update work info of a license
	 *
	 * @param username      the license username
	 * @param password      the license password
	 * @param identityStore store of the work identity
	 * @param firstName from MDM property th_firstname
	 * @param lastName from MDM property th_lastname
	 * @param csi from MDM property th_csi
	 * @param category from MDM property th_category
	 * @return result of license check (success status, error message if success = false)
	 * @throws Exception on network error
	 */
	public boolean updateWorkInfo(String username,
								  String password,
								  IdentityStoreInterface identityStore,
								  String firstName,
								  String lastName,
								  String csi,
								  String category) throws Exception {
		String url = serverUrl + "identity/update_work_info";
		JSONObject request = new JSONObject();
		request.put("licenseUsername", username);
		request.put("licensePassword", password);
		request.put("identity", identityStore.getIdentity());
		request.put("publicNickname", identityStore.getPublicNickname());
		request.put("version", version.getFullVersion());
		if (firstName != null) {
			request.put("firstName", firstName);
		}
		if (lastName != null) {
			request.put("lastName", lastName);
		}
		if (csi != null) {
			request.put("csi", csi);
		}
		if (category != null) {
			request.put("category", category);
		}

		logger.debug("Update work info phase 1: sending to server: {}", request);
		JSONObject p1Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Update work info phase 1: response from server: {}", p1Result);

		makeTokenResponse(p1Result, request, identityStore);

		/* phase 2: send token response */
		logger.debug("Update work info phase 2: sending to server: {}", request);
		JSONObject p2Result = new JSONObject(doPost(url, request.toString()));
		logger.debug("Update work info phase 2: response from server: {}", p2Result);

		if (!p2Result.getBoolean("success")) {
			throw new UpdateWorkInfoException(p2Result.getString("error"));
		}

		return true;
	}

	public int getMatchCheckInterval() {
		return matchCheckInterval;
	}

	public Version getVersion() {
		return version;
	}

	public void setVersion(Version version) {
		this.version = version;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public void setServerUrls(boolean ipv6) {
		if (this.sandbox) {
			this.serverUrl = ipv6 ? ProtocolStrings.API_SERVER_URL_SANDBOX_IPV6 : ProtocolStrings.API_SERVER_URL_SANDBOX;
			this.workServerUrl = ipv6 ? ProtocolStrings.WORK_SERVER_URL_SANDBOX_IPV6 : ProtocolStrings.WORK_SERVER_URL_SANDBOX;
		} else {
			this.serverUrl = ipv6 ? ProtocolStrings.API_SERVER_URL_IPV6 : ProtocolStrings.API_SERVER_URL;
			this.workServerUrl = ipv6 ? ProtocolStrings.WORK_SERVER_URL_IPV6 : ProtocolStrings.WORK_SERVER_URL;
		}
	}

	protected String doGet(String urlString) throws IOException {
		URL url = new URL(urlString);

		HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
		urlConnection.setSSLSocketFactory(this.sslSocketFactoryFactory.makeFactory(url.getHost()));
		urlConnection.setConnectTimeout(ProtocolDefines.API_REQUEST_TIMEOUT * 1000);
		urlConnection.setReadTimeout(ProtocolDefines.API_REQUEST_TIMEOUT * 1000);
		urlConnection.setRequestMethod("GET");
		urlConnection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT + "/" + version.getVersion());
		if (language != null) {
			urlConnection.setRequestProperty("Accept-Language", language);
		}
		urlConnection.setDoOutput(false);
		urlConnection.setDoInput(true);

		try {
			return IOUtils.toString(urlConnection.getInputStream(), StandardCharsets.UTF_8);
		} finally {
			urlConnection.disconnect();
		}
	}

	protected String doPost(String urlStr, String body) throws Exception {
		URL url = new URL(urlStr);

		HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
		urlConnection.setSSLSocketFactory(this.sslSocketFactoryFactory.makeFactory(url.getHost()));
		urlConnection.setConnectTimeout(ProtocolDefines.API_REQUEST_TIMEOUT * 1000);
		urlConnection.setReadTimeout(ProtocolDefines.API_REQUEST_TIMEOUT * 1000);
		urlConnection.setRequestMethod("POST");
		urlConnection.setRequestProperty("Content-Type", "application/json");
		urlConnection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT + "/" + version.getVersion());
		if (language != null) {
			urlConnection.setRequestProperty("Accept-Language", language);
		}
		urlConnection.setDoOutput(true);
		urlConnection.setDoInput(true);

		try {
			OutputStreamWriter osw = new OutputStreamWriter(urlConnection.getOutputStream(), StandardCharsets.UTF_8);
			osw.write(body);
			osw.close();

			InputStream inputStream = urlConnection.getInputStream();
			String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
			inputStream.close();

			return result;
		} finally {
			urlConnection.disconnect();
		}
	}

	private void makeTokenResponse(JSONObject p1Result, JSONObject request, IdentityStoreInterface identityStore) throws JSONException, IOException, ThreemaException {
		byte[] token = Base64.decode(p1Result.getString("token"));
		byte[] tokenRespKeyPub = Base64.decode(p1Result.getString("tokenRespKeyPub"));

		/* sign token with our secret key */
		byte[] nonce = new byte[NaCl.NONCEBYTES];
		random.nextBytes(nonce);

		byte[] response = identityStore.encryptData(token, nonce, tokenRespKeyPub);
		if (response == null) {
			throw new ThreemaException("TM047"); /* encryption failed */
		}

		request.put("token", Base64.encodeBytes(token));
		request.put("response", Base64.encodeBytes(response));
		request.put("nonce", Base64.encodeBytes(nonce));
	}

	public @Nullable APIConnector.FetchIdentityResult getFetchResultByIdentity(ArrayList<APIConnector.FetchIdentityResult> results, String identity) {
		if (identity != null) {
			for (APIConnector.FetchIdentityResult result : results) {
				if (identity.equals(result.identity)) {
					return result;
				}
			}
		}
		return null;
	}

	public class FetchIdentityResult {
		public String identity;
		public byte[] publicKey;
		/**
		 * @deprecated use {@link #featureMask} instead.
		 */
		@Deprecated
		public int featureLevel;
		public int featureMask;
		public int state;
		public int type;
	}

	public class FetchIdentityPrivateResult {
		public String serverGroup;
		public String email;
		public String mobileNo;
	}

	public class MatchIdentityResult {
		public byte[] publicKey;
		public byte[] mobileNoHash;
		public byte[] emailHash;
		public Object refObjectMobileNo;
		public Object refObjectEmail;
	}

	public class CheckBetaResult {
		public boolean success;
		public String error;
	}

	public class CheckLicenseResult {
		public boolean success;
		public String error;
		public String updateMessage;
		public String updateUrl;
		public String logoLightUrl;
		public String logoDarkUrl;
	}

	public class CheckIdentityStatesResult {
		public final int[] states;
		public final int[] types;
		public final String[] identities;
		public final int checkInterval;
		public final Integer[] featureMasks;

		public CheckIdentityStatesResult(int[] states,
										 int [] types,
										 String[] identities,
										 int checkInterval,
										 Integer[] featureMasks) {
			this.states = states;
			this.identities = identities;
			this.types = types;
			this.checkInterval = checkInterval;
			this.featureMasks = featureMasks;
		}
	}

	public class CheckRevocationKeyResult {
		public final boolean isSet;
		public final Date lastChanged;

		public CheckRevocationKeyResult(boolean isSet, Date lastChanged) {
			this.isSet = isSet;
			this.lastChanged = lastChanged;
		}
	}


	public class SetRevocationKeyResult {
		public final boolean success;
		public final String error;

		public SetRevocationKeyResult(boolean success, String error) {
			this.success = success;
			this.error = error;
		}
	}

	public class TurnServerInfo {
		public final String[] turnUrls;
		public final String[] turnUrlsDualStack;
		public final String turnUsername;
		public final String turnPassword;
		public final Date expirationDate;

		public TurnServerInfo(String[] turnUrls, String[] turnUrlsDualStack, String turnUsername, String turnPassword, Date expirationDate) {
			this.turnUrls = turnUrls;
			this.turnUrlsDualStack = turnUrlsDualStack;
			this.turnUsername = turnUsername;
			this.turnPassword = turnPassword;
			this.expirationDate = expirationDate;
		}
	}
}
