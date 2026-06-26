package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;

import java.util.HashMap;
import java.util.Map;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH;

public class GetLocationsRequest extends ApiRequest<GetLocationsRequest.Response> {

    public GetLocationsRequest() {
        super("GET", CH + "json/locations", Response.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");
    }

    public static class Response {
        public Map<String, Integer> locMap = new HashMap<>();
        public Map<String, String> nameMap = new HashMap<>();
        public Map<String, Integer> regionMap = new HashMap<>();
    }
}
