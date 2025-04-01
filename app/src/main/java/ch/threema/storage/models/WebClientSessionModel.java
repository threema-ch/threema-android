/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.storage.models;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

@AnyThread
public class WebClientSessionModel {

    public static final String TABLE = "wc_session";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_KEY256 = "key256";
    public static final String COLUMN_PRIVATE_KEY = "private_key";
    public static final String COLUMN_STATE = "state";
    public static final String COLUMN_CREATED = "created";
    public static final String COLUMN_LAST_CONNECTION = "last_connection";
    public static final String COLUMN_IS_PERSISTENT = "is_persistent";
    public static final String COLUMN_CLIENT_DESCRIPTION = "client";
    public static final String COLUMN_LABEL = "label";
    public static final String COLUMN_SELF_HOSTED = "self_hosted";
    public static final String COLUMN_PROTOCOL_VERSION = "protocol_version";
    public static final String COLUMN_SALTY_RTC_HOST = "salty_host";
    public static final String COLUMN_SALTY_RTC_PORT = "salty_port";
    public static final String COLUMN_SERVER_KEY = "server_key";
    public static final String COLUMN_PUSH_TOKEN = "push_token";

    private String key256;

    public enum State {
        INITIALIZING, AUTHORIZED, ERROR
    }

    private int id;
    private byte[] key;
    private byte[] privateKey;
    private Date created;
    private Date lastConnection;
    private String clientDescription;
    private State state;
    private boolean isPersistent = false;
    private String label;
    private boolean isSelfHosted = false;
    private String saltyRtcHost;
    private int saltyRtcPort;
    private byte[] serverKey;
    private String pushToken;

    public WebClientSessionModel() {
    }

    public synchronized int getId() {
        return this.id;
    }

    public synchronized WebClientSessionModel setId(int id) {
        this.id = id;
        return this;
    }

    /**
     * Return the trusted peer public key.
     */
    public synchronized byte[] getKey() {
        return this.key;
    }

    public synchronized WebClientSessionModel setKey(byte[] key) {
        this.key = key;
        return this;
    }

    /**
     * Return the SHA256 hash of the trusted peer public key.
     */
    public synchronized String getKey256() {
        return this.key256;
    }

    public synchronized WebClientSessionModel setKey256(String key256) {
        this.key256 = key256;
        return this;
    }

    @Nullable
    public synchronized Date getCreated() {
        return this.created;
    }

    @Nullable
    public synchronized Date getLastConnection() {
        return this.lastConnection;
    }

    public synchronized WebClientSessionModel setCreated(@NonNull Date created) {
        this.created = created;
        return this;
    }

    public synchronized WebClientSessionModel setLastConnection(@NonNull Date lastConnection) {
        this.lastConnection = lastConnection;
        return this;
    }

    public synchronized String getClientDescription() {
        return this.clientDescription;
    }

    public synchronized WebClientSessionModel setClientDescription(String clientDescription) {
        this.clientDescription = clientDescription;
        return this;
    }

    public synchronized State getState() {
        return this.state;
    }

    public synchronized WebClientSessionModel setState(State state) {
        this.state = state;
        return this;
    }

    /**
     * Return the session private key.
     */
    public synchronized byte[] getPrivateKey() {
        return this.privateKey;
    }

    public synchronized WebClientSessionModel setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public synchronized boolean isPersistent() {
        return this.isPersistent;
    }

    public synchronized WebClientSessionModel setPersistent(boolean persistent) {
        isPersistent = persistent;
        return this;
    }

    public synchronized String getLabel() {
        return label;
    }

    public synchronized WebClientSessionModel setLabel(String label) {
        this.label = label;
        return this;
    }

    public synchronized WebClientSessionModel setSelfHosted(boolean selfHosted) {
        this.isSelfHosted = selfHosted;
        return this;
    }

    public synchronized boolean isSelfHosted() {
        return this.isSelfHosted;
    }

    public synchronized WebClientSessionModel setSaltyRtcHost(String saltyRtcHost) {
        this.saltyRtcHost = saltyRtcHost;
        return this;
    }

    public synchronized String getSaltyRtcHost() {
        return this.saltyRtcHost;
    }

    public synchronized WebClientSessionModel setSaltyRtcPort(int saltyRtcPort) {
        this.saltyRtcPort = saltyRtcPort;
        return this;
    }

    public synchronized int getSaltyRtcPort() {
        return this.saltyRtcPort;
    }

    public synchronized WebClientSessionModel setServerKey(byte[] serverKey) {
        this.serverKey = serverKey;
        return this;
    }

    public synchronized byte[] getServerKey() {
        return this.serverKey;
    }

    public synchronized WebClientSessionModel setPushToken(String token) {
        this.pushToken = token;
        return this;
    }

    public synchronized String getPushToken() {
        return this.pushToken;
    }

    @Override
    public synchronized String toString() {
        return (this.id + " " + this.label + " " + this.clientDescription).trim();
    }
}


