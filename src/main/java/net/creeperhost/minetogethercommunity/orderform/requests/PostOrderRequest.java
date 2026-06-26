package net.creeperhost.minetogethercommunity.orderform.requests;

import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogether.lib.web.ApiResponse;
import net.creeperhost.minetogether.lib.web.WebUtils.UrlParamPair;
import net.creeperhost.minetogethercommunity.orderform.data.Order;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH;

public class PostOrderRequest extends ApiRequest<PostOrderRequest.Response> {

    public PostOrderRequest(Order order, String dcId, String pregen, String fallbackName) {
        super("POST", CH + "json/order/" + order.clientID + "/" + order.productID + "/" + dcId, Response.class);
        requiredAuthHeaders.add("Fingerprint");
        requiredAuthHeaders.add("Identifier");

        List<UrlParamPair> entries = new ArrayList<>();
        entries.add(UrlParamPair.of("name", order.name));
        entries.add(UrlParamPair.of("swid", ModPackInfo.getInfo().websiteID));

        if (order.pregen) {
            entries.add(UrlParamPair.of("pregen", pregen));
        }
        if (StringUtils.isNotBlank(order.worldUrl)) {
            entries.add(UrlParamPair.of("worldUrl", order.worldUrl));
        }
        if (StringUtils.isNotBlank(fallbackName)) {
            entries.add(UrlParamPair.of("fallback", fallbackName));
        }

        UUID uuid = Minecraft.getMinecraft().getSession().getProfile().getId();
        if (uuid != null) {
            entries.add(UrlParamPair.of("ownerUuid", uuid.toString()));
        }

        formBody(entries);
    }

    public static class Response extends ApiResponse {
        public Data more;

        public Response(String status, String message) {
            super(status, message);
        }
    }

    public static class Data {
        public String invoiceid;
        public String orderid;
    }
}
