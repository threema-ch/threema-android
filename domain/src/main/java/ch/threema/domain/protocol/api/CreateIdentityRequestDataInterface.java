package ch.threema.domain.protocol.api;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface CreateIdentityRequestDataInterface {
    @NonNull
    JSONObject createIdentityRequestDataJSON() throws JSONException;
}
