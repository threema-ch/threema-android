/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import ch.threema.base.BuilderException;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder to create a {@link BallotDataChoice} entry.
 */
public class BallotDataChoiceBuilder {
    private Integer id;
    private String description;
    private Integer sortKey;
    private List<Integer> votes;

    /**
     * Set the ID, A per-poll unique ID of the choice in form of an integer. Used when casting a vote.
     */
    public BallotDataChoiceBuilder setId(int id) {
        this.id = id;
        return this;
    }

    /**
     * Set the choice description / text.
     */
    public BallotDataChoiceBuilder setDescription(@NonNull String description) {
        this.description = description;
        return this;
    }

    /**
     * Index used for sorting. Soft-deprecated, the index in the parent array should be used instead.
     * Set this for backwards compatibility.
     */
    public BallotDataChoiceBuilder setSortKey(int sortKey) {
        this.sortKey = sortKey;
        return this;
    }

    /**
     * The index of the participant (as defined in the participants list) that casted a vote for this choice.
     * This field must only be present if the poll is being closed.
     */
    public BallotDataChoiceBuilder addVote(int participantIndex) {
        if (this.votes == null) {
            this.votes = new ArrayList<>();
        }
        this.votes.add(participantIndex);
        return this;
    }

    /**
     * Validate and return a {@link BallotDataChoice}.
     *
     * @throws BuilderException if not all required fields were set.
     */
    public BallotDataChoice build() throws BuilderException {
        if (this.id == null) {
            throw new BuilderException("Cannot build BallotDataChoice: id is null");
        }
        if (this.description == null) {
            throw new BuilderException("Cannot build BallotDataChoice: description is null");
        }
        if (this.sortKey == null) {
            throw new BuilderException("Cannot build BallotDataChoice: sortKey is null");
        }
        final BallotDataChoice choice = new BallotDataChoice(this.votes == null ? 0 : this.votes.size());
        choice.id = this.id;
        choice.name = this.description;
        choice.order = this.sortKey;
        if (this.votes != null) {
            for (int i = 0; i < this.votes.size(); i++) {
                choice.ballotDataChoiceResults[i] = this.votes.get(i);
            }
        }
        return choice;
    }
}
