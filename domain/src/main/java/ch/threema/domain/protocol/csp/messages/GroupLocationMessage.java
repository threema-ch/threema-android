/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A group message that has a GPS location with accuracy as its contents.
 *
 * Coordinates are in WGS 84, accuracy is in meters.
 */
public class GroupLocationMessage extends AbstractGroupMessage {

	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupLocationMessage");

	private double latitude;
	private double longitude;
	private double accuracy;
	private String poiName;
	private String poiAddress;

	public GroupLocationMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_GROUP_LOCATION;
	}

	@Override
	public boolean flagSendPush() {
		return true;
	}

	@Override
	@Nullable
	public Version getMinimumRequiredForwardSecurityVersion() {
		return Version.V1_2;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return true;
	}

	@Override
	public boolean exemptFromBlocking() {
		return false;
	}

	@Override
	public boolean createImplicitlyDirectContact() {
		return false;
	}

	@Override
	public boolean protectAgainstReplay() {
		return true;
	}

	@Override
	public boolean reflectIncoming() {
		return true;
	}

	@Override
	public boolean reflectOutgoing() {
		return true;
	}

	@Override
	public boolean reflectSentUpdate() {
		return true;
	}

	@Override
	public boolean sendAutomaticDeliveryReceipt() {
		return false;
	}

	@Override
	public boolean bumpLastUpdate() {
		return true;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(getGroupCreator().getBytes(StandardCharsets.US_ASCII));
			bos.write(getApiGroupId().getGroupId());
			String locStr = String.format(Locale.US, "%f,%f,%f", latitude, longitude, accuracy);
			if (poiName != null)
				locStr += "\n" + poiName;
			if (poiAddress != null)
				locStr += "\n" + poiAddress.replace("\n", "\\n");
			bos.write(locStr.getBytes(StandardCharsets.UTF_8));
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public double getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(double accuracy) {
		this.accuracy = accuracy;
	}

	public String getPoiName() {
		return poiName;
	}

	public void setPoiName(String poiName) {
		this.poiName = poiName;
	}

	public String getPoiAddress() {
		return poiAddress;
	}

	public void setPoiAddress(String poiAddress) {
		this.poiAddress = poiAddress;
	}
}
