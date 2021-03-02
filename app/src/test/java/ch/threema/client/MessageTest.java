/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2017-2021 Threema GmbH
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

import ch.threema.base.ThreemaException;
import ch.threema.client.voip.*;
import org.junit.Assert;
import org.junit.Test;

public class MessageTest {

	private BoxedMessage box(AbstractMessage msg) throws ThreemaException {
		return msg.makeBox(
				Helpers.getContactStore(),
				Helpers.getIdentityStore(),
				Helpers.getNonceFactory()
		);
	}

	@Test
	public void testVoipFlagsOffer() throws ThreemaException {
		final VoipCallOfferMessage msg = new VoipCallOfferMessage();
		final VoipCallOfferData offerData = new VoipCallOfferData();
		final VoipCallOfferData.OfferData data = new VoipCallOfferData.OfferData()
			.setSdp("testsdp")
			.setSdpType("offer");
		offerData.setOfferData(data);
		msg.setData(offerData);
		final BoxedMessage boxed = this.box(msg);
		// Flags: Voip + Push
		Assert.assertEquals(0x20 | 0x01, boxed.getFlags());
	}

	@Test
	public void testVoipFlagsAnswer() throws ThreemaException {
		final VoipCallAnswerMessage msg = new VoipCallAnswerMessage();
		final VoipCallAnswerData answerData = new VoipCallAnswerData()
			.setAction(VoipCallAnswerData.Action.REJECT)
			.setAnswerData(null)
			.setRejectReason(VoipCallAnswerData.RejectReason.BUSY);
		msg.setData(answerData);
		final BoxedMessage boxed = this.box(msg);
		// Flags: Voip + Push
		Assert.assertEquals(0x20 | 0x01, boxed.getFlags());
	}

	@Test
	public void testVoipFlagsCandidates() throws ThreemaException {
		final VoipICECandidatesMessage msg = new VoipICECandidatesMessage();
		final VoipICECandidatesData candidatesData = new VoipICECandidatesData()
			.setCandidates(new VoipICECandidatesData.Candidate[]{
				new VoipICECandidatesData.Candidate("testcandidate1", "testmid1", 42, "testufrag1"),
				new VoipICECandidatesData.Candidate("testcandidate2", "testmid2", 23, "testufrag2"),
			});
		msg.setData(candidatesData);
		final BoxedMessage boxed = this.box(msg);
		// Flags: Voip (Now it should push, my friend)
		Assert.assertEquals(0x21, boxed.getFlags());
	}

	@Test
	public void testVoipFlagsHangup() throws ThreemaException {
		final VoipCallHangupMessage msg = new VoipCallHangupMessage();
		msg.setData(new VoipCallHangupData());
		final BoxedMessage boxed = this.box(msg);
		// Flags: Voip + Push
		Assert.assertEquals(0x20 | 0x01, boxed.getFlags());
	}

}
