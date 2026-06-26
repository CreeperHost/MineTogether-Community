package net.creeperhost.minetogethercommunity.activity;

import com.google.common.collect.ImmutableMap;
import net.creeperhost.minetogether.lib.chat.request.v2.Apiv2Response;
import net.creeperhost.minetogether.lib.web.ApiRequest;

import static net.creeperhost.minetogether.lib.web.WebConstants.MT_API;

public class PutTelemetryPreferencesRequest extends ApiRequest<PutTelemetryPreferencesRequest.Response> {

    public PutTelemetryPreferencesRequest(boolean enabled) {
        super("PUT", MT_API + "v2/telemetry/preferences", Response.class);
        requiredAuthHeaders.add("Authorization");
        jsonBody(GSON, ImmutableMap.of("enabled", enabled));
    }

    public static class Response extends Apiv2Response {
        public boolean enabled;
        public boolean hasAccount;
    }
}
