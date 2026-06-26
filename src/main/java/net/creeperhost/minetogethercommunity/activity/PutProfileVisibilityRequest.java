package net.creeperhost.minetogethercommunity.activity;

import com.google.common.collect.ImmutableMap;
import net.creeperhost.minetogether.lib.chat.request.v2.Apiv2Response;
import net.creeperhost.minetogether.lib.web.ApiRequest;

import static net.creeperhost.minetogether.lib.web.WebConstants.MT_API;

public class PutProfileVisibilityRequest extends ApiRequest<PutProfileVisibilityRequest.Response> {

    public PutProfileVisibilityRequest(String visibility) {
        super("PUT", MT_API + "v2/profile/visibility", Response.class);
        requiredAuthHeaders.add("Authorization");
        jsonBody(GSON, ImmutableMap.of("visibility", visibility));
    }

    public static class Response extends Apiv2Response {
        public String visibility = "public";
        public boolean hasAccount;
    }
}
