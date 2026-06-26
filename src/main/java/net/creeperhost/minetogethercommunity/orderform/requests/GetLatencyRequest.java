package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogether.lib.web.ApiResponse;

public class GetLatencyRequest extends ApiRequest<GetLatencyRequest.Response> {

    public GetLatencyRequest(String latencyUrl) {
        super("GET", latencyUrl, Response.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");
    }

    public static class Response extends ApiResponse {
        public double latency;
        public int hops;
        public boolean accurate;
        public String node;
    }
}
