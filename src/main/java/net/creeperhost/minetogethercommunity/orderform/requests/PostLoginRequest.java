package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogether.lib.web.ApiResponse;
import net.creeperhost.minetogether.lib.web.WebUtils.UrlParamPair;

import java.util.ArrayList;
import java.util.List;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH;

public class PostLoginRequest extends ApiRequest<PostLoginRequest.Response> {

    public PostLoginRequest(String email, String password) {
        super("POST", CH + "json/account/login", Response.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");

        List<UrlParamPair> entries = new ArrayList<>();
        entries.add(UrlParamPair.of("email", email));
        entries.add(UrlParamPair.of("password", password));
        formBody(entries);
    }

    public static class Response extends ApiResponse {
        public String userid;
        public String currency;

        public Response(String status, String message) {
            super(status, message);
        }
    }
}
