package net.creeperhost.minetogethercommunity.activity;

import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogether.lib.web.ApiResponse;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH_API;

public class PostActivityBatchRequest extends ApiRequest<PostActivityBatchRequest.Response> {

    public PostActivityBatchRequest(ActivityModels.Batch batch) {
        super("POST", CH_API + "minetogether/activity/batch", Response.class);
        requiredAuthHeaders.add("Authorization");
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");
        jsonBody(batch);
    }

    public static class Response extends ApiResponse {
        public boolean accepted = true;
    }
}
