/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.storage.models.ballot;


import java.util.Date;

public class BallotModel {
    public static final String TABLE = "ballot";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_API_BALLOT_ID = "apiBallotId";
    public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_STATE = "state";
    public static final String COLUMN_ASSESSMENT = "assessment";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_CHOICE_TYPE = "choiceType";
    public static final String COLUMN_DISPLAY_TYPE = "displayType";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_MODIFIED_AT = "modifiedAt";
    public static final String COLUMN_LAST_VIEWED_AT = "lastViewedAt";

    public enum State {
        TEMPORARY, OPEN, CLOSED
    }

    public enum Assessment {
        SINGLE_CHOICE, MULTIPLE_CHOICE
    }

    public enum Type {
        RESULT_ON_CLOSE, INTERMEDIATE
    }

    public enum ChoiceType {
        TEXT
    }

    public enum DisplayType {
        LIST_MODE, SUMMARY_MODE
    }

    private int id;
    private String apiBallotId;
    private String creatorIdentity;
    private String name;
    private State state;
    private Assessment assessment;
    private Type type;
    private ChoiceType choiceType;
    private DisplayType displayType;
    private Date createdAt;
    private Date modifiedAt;
    private Date lastViewedAt;

    public int getId() {
        return id;
    }

    public BallotModel setId(int id) {
        this.id = id;
        return this;
    }

    public String getApiBallotId() {
        return apiBallotId;
    }

    public BallotModel setApiBallotId(String apiBallotId) {
        this.apiBallotId = apiBallotId;
        return this;
    }

    public String getCreatorIdentity() {
        return creatorIdentity;
    }

    public BallotModel setCreatorIdentity(String creatorIdentity) {
        this.creatorIdentity = creatorIdentity;
        return this;
    }

    public String getName() {
        return name;
    }

    public BallotModel setName(String name) {
        this.name = name;
        return this;
    }

    public State getState() {
        return state;
    }

    public BallotModel setState(State state) {
        this.state = state;
        return this;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public BallotModel setAssessment(Assessment assessment) {
        this.assessment = assessment;
        return this;
    }

    public Type getType() {
        return this.type;
    }

    public BallotModel setType(Type type) {
        this.type = type;
        return this;
    }

    public ChoiceType getChoiceType() {
        return choiceType;
    }

    public BallotModel setChoiceType(ChoiceType choiceType) {
        this.choiceType = choiceType;
        return this;
    }

    public DisplayType getDisplayType() {
        return displayType;
    }

    public BallotModel setDisplayType(DisplayType displayType) {
        this.displayType = displayType;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public BallotModel setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    public BallotModel setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
        return this;
    }

    public Date getLastViewedAt() {
        return this.lastViewedAt;
    }

    public BallotModel setLastViewedAt(Date lastViewedAt) {
        this.lastViewedAt = lastViewedAt;
        return this;
    }

}
