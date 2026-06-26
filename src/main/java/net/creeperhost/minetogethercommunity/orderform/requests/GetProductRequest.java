package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH;

public class GetProductRequest extends ApiRequest<GetProductRequest.Response> {

    public GetProductRequest(String product) {
        super("GET", CH + "json/products/" + product, Response.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");
    }

    public static class Response {
        public int id;
        public String name;
        public String displayName;
        public String description;
        public boolean display;
        public int quantity;
        public List<Price> pricing = new ArrayList<>();
    }

    public static class Price {
        public int id;
        public String prefix;
        public double rate;
        public String code;
        public String suffix;
        public Map<String, Double> amount = new HashMap<>();
    }
}
