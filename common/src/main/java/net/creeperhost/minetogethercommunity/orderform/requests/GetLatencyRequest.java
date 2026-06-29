package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogether.lib.web.ApiResponse;

/**
 * Created by brandon3055 on 24/06/2024
 */
public class GetLatencyRequest extends ApiRequest<GetLatencyRequest.Response> {

    public GetLatencyRequest(String latencyURL) {
        super("GET", latencyURL, Response.class);
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
