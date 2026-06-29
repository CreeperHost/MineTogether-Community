package net.creeperhost.minetogethercommunity.orderform;

import net.covers1624.quack.net.httpapi.EngineRequest;
import net.covers1624.quack.net.httpapi.EngineResponse;
import net.covers1624.quack.net.httpapi.WebBody;
import net.creeperhost.minetogether.lib.util.Countries;
import net.creeperhost.minetogether.lib.web.ApiResponse;
import net.creeperhost.minetogether.lib.web.requests.GetClosestDCRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.orderform.data.Order;
import net.creeperhost.minetogethercommunity.orderform.data.OrderSummary;
import net.creeperhost.minetogethercommunity.orderform.requests.*;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.creeperhost.minetogethercommunity.MineTogether.WEB_ENGINE;

/**
 * Created by brandon3055 on 24/06/2024
 */
public class OrderRequests {

    private static final Logger LOGGER = LogManager.getLogger();

    public static List<GetDataCentresRequest.DC> getDataCenters(int ram) {
        try {
            GetDataCentresRequest.Response response = MineTogether.API.execute(new GetDataCentresRequest(ram)).apiResponse();
            if (!response.getStatus().equals("success")) {
                LOGGER.error("Failed to retrieve datacenter list. Api returned: {}", response.getMessageOrNull());
                return Collections.emptyList();
            }
            return response.dataCentres != null ? response.dataCentres : Collections.emptyList();
        } catch (Throwable e) {
            LOGGER.error("Failed to retrieve datacenter list", e);
            return Collections.emptyList();
        }
    }

    public static Map<String, Integer> getLocations() {
        try {
            GetLocationsRequest.Response response = MineTogether.API.execute(new GetLocationsRequest()).apiResponse();
            return response.locMap != null ? response.locMap : Collections.emptyMap();
        } catch (Throwable e) {
            LOGGER.error("Failed to retrieve location list", e);
            return Collections.emptyMap();
        }
    }

    public static GetClosestDCRequest.Response getDCsByDistance() {
        try {
            return MineTogether.API.execute(new GetClosestDCRequest()).apiResponse();
        } catch (Throwable e) {
            LOGGER.error("Failed to retrieve DCs by distance", e);
            return null;
        }
    }

    public static int getDCLatency(String latencyUrl, long distance) {
        try {
            GetLatencyRequest.Response response = MineTogether.API.execute(new GetLatencyRequest(latencyUrl)).apiResponse();
            if (!response.getStatus().equals("success")) {
                LOGGER.error("Failed to check DC Latency. Api returned: {}", response.getMessageOrNull());
                return -1;
            }

            double latency = response.latency;
            double milesPerSecond = 124188; //This is the miles per second value for light using the average refractive index of single mode fibre.
            double minMs = ((distance / milesPerSecond) * 1000) * 1.7; //Figure used against real world RTT time to get close.
            if (latency < minMs) latency = Math.round(minMs);
            return (int) Math.max(latency, 1); //Set hard minimum of 1ms latency.
        } catch (Throwable e) {
            LOGGER.error("Failed to check DC Latency", e);
            return -1;
        }
    }

    public static OrderSummary getSummary(Order order, String promo) {
        if (order.country.isEmpty()) order.country = Countries.getOurCountry(MineTogether.API);
        if (order.serverLocation.isEmpty()) {
            order.serverLocation = getDCsByDistance().getDataCenter().getName();
        }

        String version = ModPackInfo.getInfo().websiteID;
        if (version.isEmpty()) version = "0";

        try {
            var recResponse = MineTogether.API.execute(new GetRecommendRequest(version, order.playerAmount)).apiResponse();
            String recommended = String.valueOf(recResponse.recommended);
            if (StringUtils.isNotEmpty(promo) && !promo.equalsIgnoreCase("Insert Promo Code here")) {
                //TODO How to we do a simple get where we dont care about the response? Currently things break if the response is not json. This returns a web page.
                getWebResponse("https://www.creeperhost.net/applyPromo/" + promo);
            }

            var summary = MineTogether.API.execute(new GetSummaryRequest(order.country, recommended)).apiResponse();
            var op0 = summary.option0;
            double preDiscount = op0.preDiscount;
            double subTotal = op0.subtotal;
            double discount = op0.discount == null ? 0 : op0.discount;
            double tax = op0.tax;
            if (tax <= 0) tax = 0.0;
            double total = op0.total;

            var currencyResponse = MineTogether.API.execute(new GetCurrencyRequest(order.country)).apiResponse();
            String prefix = currencyResponse.prefix;
            String suffix = currencyResponse.suffix;
            String id = currencyResponse.id;

            var productResponse = MineTogether.API.execute(new GetProductRequest(recommended)).apiResponse();
            String vpsDisplay = productResponse.displayName;
            String vpsDescription = productResponse.description;
            String patternStr = "<li>(.*?)<";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(vpsDescription);

            ArrayList<String> vpsFeatures = new ArrayList<>();
            while (matcher.find()) {
                String group = matcher.group(1);
                vpsFeatures.add(group);
            }

            List<String> vpsIncluded = new ArrayList<>();
            vpsIncluded.add("minetogether.quote.vpsincluded1");
            vpsIncluded.add("minetogether.quote.vpsincluded2");
            vpsIncluded.add("minetogether.quote.vpsincluded3");
            vpsIncluded.add("minetogether.quote.vpsincluded4");
            vpsIncluded.add("minetogether.quote.vpsincluded5");
            vpsIncluded.add("minetogether.quote.vpsincluded6");
            vpsIncluded.add("minetogether.quote.vpsincluded7");

            return new OrderSummary(recommended, vpsDisplay, vpsFeatures, vpsIncluded, preDiscount, subTotal, total, tax, discount, suffix, prefix, id, recResponse.ram);
        } catch (Throwable e) {
            LOGGER.error("Unable to fetch summary", e);
            return new OrderSummary("Unable to fetch summary");
        }
    }

    @Deprecated
    private static String getWebResponse(String urlString) throws IOException {
        EngineRequest request = WEB_ENGINE.newRequest()
                .method("GET", null)
                .url(urlString)
                .header("User-Agent", WebUtils.userAgent)
                .header("Fingerprint", MineTogether.FINGERPRINT)
                .header("Identifier", ModPackInfo.getInfo().realName);

        try (EngineResponse response = request.execute()) {
            WebBody entity = response.body();
            if (entity == null) {
                return "";
            }
            return IOUtils.toString(entity.open(), StandardCharsets.UTF_8);
        }
    }

    public static ApiResponse getNameAvailable(String name) {
        try {
            return MineTogether.API.execute(new GetNameAvailableRequest(name)).apiResponse();
        } catch (Throwable e) {
            LOGGER.error("Failed to retrieve datacenter list", e);
            return new ApiResponse("error", "unknown");
        }
    }

    public static boolean doesAccountExist(String email) {
        try {
            ApiResponse response = MineTogether.API.execute(new PostEmailExistsRequest(email)).apiResponse();
            if ("error".equals(response.getStatus())) {
                return true; //"Error" status means email "does" exist
            }
        } catch (Throwable e) {
            LOGGER.error("Unable to check if email exists", e);
        }
        return false;
    }

    public static PostLoginRequest.Response doLogin(String email, String password) {
        try {
            PostLoginRequest.Response response = MineTogether.API.execute(new PostLoginRequest(email, password)).apiResponse();
            if (!response.getStatus().equals("success")) {
                LOGGER.error("Failed to complete login. Api returned: {}", response.getMessageOrNull());
            }
            return response;
        } catch (Throwable e) {
            LOGGER.error("An error occurred while attempting to login", e);
            return new PostLoginRequest.Response("error", "Unknown Error");
        }
    }

    public static PostOrderRequest.Response placeOrder(Order order, String dcId, String pregen, String fallbackName) {
        try {
            PostOrderRequest.Response response = MineTogether.API.execute(new PostOrderRequest(order, dcId, pregen, fallbackName)).apiResponse();
            if (!response.getStatus().equals("success")) {
                LOGGER.error("Failed to complete login. Api returned: {}", response.getMessageOrNull());
            }
            return response;
        } catch (Throwable e) {
            LOGGER.error("Unable to create order", e);
            return new PostOrderRequest.Response("error", "Unknown Error");
        }
    }

    public static PostCreateAccountRequest.Response createAccount(Order order) {
        try {
            PostCreateAccountRequest.Response response = MineTogether.API.execute(new PostCreateAccountRequest(order)).apiResponse();
            if (!response.getStatus().equals("success")) {
                LOGGER.error("Failed to create account. Api returned: {}", response.getMessageOrNull());
            }
            return response;
        } catch (Throwable e) {
            LOGGER.error("Unable to create account", e);
            return new PostCreateAccountRequest.Response("error", "Unknown Error");
        }
    }
}
