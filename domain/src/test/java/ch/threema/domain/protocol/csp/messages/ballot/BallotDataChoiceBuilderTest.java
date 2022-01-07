/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

import ch.threema.base.BuilderException;
import org.junit.Assert;
import org.junit.Test;

public class BallotDataChoiceBuilderTest {
	@Test
	public void buildValidNoVotes() {
		final BallotDataChoice choice = new BallotDataChoiceBuilder()
			.setId(123)
			.setDescription("This is a choice")
			.setSortKey(1)
			.build();
		Assert.assertEquals(123, choice.getId());
		Assert.assertEquals("This is a choice", choice.getName());
		Assert.assertEquals(1, choice.getOrder());
		Assert.assertNull(choice.getResult(0));
	}

	@Test
	public void buildValidWithVotes() {
		final BallotDataChoice choice = new BallotDataChoiceBuilder()
			.setId(123)
			.setDescription("This is a choice")
			.setSortKey(1)
			.addVote(3)
			.addVote(1)
			.addVote(2)
			.build();
		Assert.assertEquals(Integer.valueOf(3), choice.getResult(0));
		Assert.assertEquals(Integer.valueOf(1), choice.getResult(1));
		Assert.assertEquals(Integer.valueOf(2), choice.getResult(2));
	}

	@Test
	public void buildInvalidNoId() {
		final BallotDataChoiceBuilder builder = new BallotDataChoiceBuilder()
			.setDescription("This is a choice")
			.setSortKey(1);
		try {
			builder.build();
			Assert.fail("BuilderException not thrown");
		} catch (BuilderException e) {
			Assert.assertEquals("Cannot build BallotDataChoice: id is null", e.getMessage());
		}
	}

	@Test
	public void buildInvalidNoDescription() {
		final BallotDataChoiceBuilder builder = new BallotDataChoiceBuilder()
			.setId(987)
			.setSortKey(1);
		try {
			builder.build();
			Assert.fail("BuilderException not thrown");
		} catch (BuilderException e) {
			Assert.assertEquals("Cannot build BallotDataChoice: description is null", e.getMessage());
		}
	}

	@Test
	public void buildInvalidNoSortKey() {
		final BallotDataChoiceBuilder builder = new BallotDataChoiceBuilder()
			.setId(987)
			.setDescription("This is a choice");
		try {
			builder.build();
			Assert.fail("BuilderException not thrown");
		} catch (BuilderException e) {
			Assert.assertEquals("Cannot build BallotDataChoice: sortKey is null", e.getMessage());
		}
	}
}
