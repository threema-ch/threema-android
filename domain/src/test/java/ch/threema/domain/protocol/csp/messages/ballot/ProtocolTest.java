/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.ballot;

import ch.threema.base.crypto.NonceFactory;
import ch.threema.domain.stores.TestHelpers;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;

import org.junit.Assert;
import org.junit.Test;

public class ProtocolTest {

	@Test
	public void groupTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		//create a new ballot
		final String myIdentity = "TESTTST";
		final String toIdentity = "ABCDEFGH";

		BallotId ballotId = new BallotId(new byte[ProtocolDefines.BALLOT_ID_LEN]);
		String ballotCreator = toIdentity;

		GroupId groupId = new GroupId(new byte[ProtocolDefines.GROUP_ID_LEN]);
		String groupCreator = ballotCreator;

		GroupBallotCreateMessage b = new GroupBallotCreateMessage();
		b.setFromIdentity(ballotCreator);
		b.setToIdentity(myIdentity);
		b.setGroupId(groupId);
		b.setGroupCreator(groupCreator);
		b.setBallotId(ballotId);
		b.setBallotCreator(ballotCreator);
		BallotData data = new BallotData();
		data.setDescription("Test Ballot");
		data.setType(BallotData.Type.RESULT_ON_CLOSE);
		data.setAssessmentType(BallotData.AssessmentType.SINGLE);
		data.setState(BallotData.State.OPEN);
		data.setDisplayType(BallotData.DisplayType.LIST_MODE);


		for(int n = 0; n < 10; n++) {
			BallotDataChoice c = new BallotDataChoice(2);
			c.setId(n+1);
			c.setName("Choice " + (n+1));
			c.setOrder(n);
			c.addResult(0, 1).addResult(1,0);
			c.setTotalVotes(2);
			data.getChoiceList().add(c);
		}
		b.setData(data);

		ContactStore contactStore = TestHelpers.getNoopContactStore();
		IdentityStoreInterface identityStore = TestHelpers.getNoopIdentityStore();
		NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();
		MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

		MessageBox boxmsg = messageCoder.encode(b, nonceFactory);
		Assert.assertNotNull("BoxMessage failed", boxmsg);

		//now decode again
		AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg, true);
		Assert.assertNotNull("decodedBox failed", decodedBoxMessage);
		Assert.assertTrue(decodedBoxMessage instanceof GroupBallotCreateMessage);

		GroupBallotCreateMessage db = (GroupBallotCreateMessage) decodedBoxMessage;

		BallotData d = db.getData();
		Assert.assertNotNull(d);

		Assert.assertEquals(BallotData.State.OPEN,  d.getState());
		Assert.assertEquals(BallotData.AssessmentType.SINGLE, d.getAssessmentType());
		Assert.assertEquals(BallotData.Type.RESULT_ON_CLOSE, d.getType());
		Assert.assertEquals(10, b.getData().getChoiceList().size());
		Assert.assertEquals("Choice 7", b.getData().getChoiceList().get(6).getName());
		Assert.assertEquals(1, (int) b.getData().getChoiceList().get(2).getResult(0));
		Assert.assertEquals(0, (int) b.getData().getChoiceList().get(2).getResult(1));
	}


	@Test
	public void identityTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		//create a new ballot
		final String myIdentity = "TESTTST";
		final String toIdentity = "ABCDEFGH";

		BallotId ballotId = new BallotId(new byte[ProtocolDefines.BALLOT_ID_LEN]);
		String ballotCreator = toIdentity;


		BallotCreateMessage b = new BallotCreateMessage();
		b.setFromIdentity(ballotCreator);
		b.setToIdentity(myIdentity);
		b.setBallotId(ballotId);
		b.setBallotCreator(ballotCreator);
		BallotData data = new BallotData();
		data.setDescription("Test Ballot");
		data.setType(BallotData.Type.RESULT_ON_CLOSE);
		data.setAssessmentType(BallotData.AssessmentType.SINGLE);
		data.setState(BallotData.State.OPEN);


		for(int n = 0; n < 10; n++) {
			BallotDataChoice c = new BallotDataChoice(2);
			c.setId(n+1);
			c.setName("Choice " + (n+1));
			c.setOrder(n);
			c.addResult(0, 1).addResult(1,0);
			data.getChoiceList().add(c);
		}
		b.setData(data);

		ContactStore contactStore = TestHelpers.getNoopContactStore();
		IdentityStoreInterface identityStore = TestHelpers.getNoopIdentityStore();
		MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

		NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();

		MessageBox boxmsg = messageCoder.encode(b, nonceFactory);
		Assert.assertNotNull("BoxMessage failed", boxmsg);

		//now decode again
		AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg, true);
		Assert.assertNotNull("decodedBox failed", decodedBoxMessage);
		Assert.assertTrue(decodedBoxMessage instanceof BallotCreateMessage);

		BallotCreateMessage db = (BallotCreateMessage) decodedBoxMessage;

		BallotData d = db.getData();
		Assert.assertNotNull(d);

		Assert.assertEquals(BallotData.State.OPEN,  d.getState());
		Assert.assertEquals(BallotData.AssessmentType.SINGLE, d.getAssessmentType());
		Assert.assertEquals(BallotData.Type.RESULT_ON_CLOSE, d.getType());
		Assert.assertEquals(10, b.getData().getChoiceList().size());
		Assert.assertEquals("Choice 7", b.getData().getChoiceList().get(6).getName());
		Assert.assertEquals(1, (int) b.getData().getChoiceList().get(2).getResult(0));
		Assert.assertEquals(0, (int) b.getData().getChoiceList().get(2).getResult(1));
	}
}
