package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH;

public class GetRecommendRequest extends ApiRequest<GetRecommendRequest.Response> {

    public GetRecommendRequest(String version, int playerCount) {
        super("GET", CH + "json/order/mc/" + version + "/recommend/" + playerCount, Response.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");
    }

    public static class Response {
        public int recommended;
        public int ram;
    }
}
