package net.creeperhost.minetogethercommunity.activity;

import net.creeperhost.minetogether.lib.chat.request.v2.Apiv2Response;
import net.creeperhost.minetogether.lib.web.ApiRequest;

import static net.creeperhost.minetogether.lib.web.WebConstants.MT_API;

public class GetTelemetryPreferencesRequest extends ApiRequest<GetTelemetryPreferencesRequest.Response> {

    public GetTelemetryPreferencesRequest() {
        super("GET", MT_API + "v2/telemetry/preferences", Response.class);
        requiredAuthHeaders.add("Authorization");
    }

    public static class Response extends Apiv2Response {
        public boolean enabled = true;
        public boolean hasAccount = false;
    }
}
