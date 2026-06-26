package net.creeperhost.minetogethercommunity.activity;

import net.creeperhost.minetogether.lib.chat.request.v2.Apiv2Response;
import net.creeperhost.minetogether.lib.web.ApiRequest;

import static net.creeperhost.minetogether.lib.web.WebConstants.MT_API;

public class GetProfileVisibilityRequest extends ApiRequest<GetProfileVisibilityRequest.Response> {

    public GetProfileVisibilityRequest() {
        super("GET", MT_API + "v2/profile/visibility", Response.class);
        requiredAuthHeaders.add("Authorization");
    }

    public static class Response extends Apiv2Response {
        public String visibility = "public";
        public boolean hasAccount = false;
    }
}
