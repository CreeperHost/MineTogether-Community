package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogether.lib.web.ApiResponse;
import net.creeperhost.minetogether.lib.web.WebConstants;

public class GetNameAvailableRequest extends ApiRequest<ApiResponse> {

    public GetNameAvailableRequest(String name) {
        super("GET", WebConstants.CH + "json/availability/" + name, ApiResponse.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");
    }
}
