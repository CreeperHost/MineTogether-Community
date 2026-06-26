package net.creeperhost.minetogethercommunity.orderform;

import net.creeperhost.minetogether.lib.util.Countries;
import net.creeperhost.minetogether.lib.web.ApiResponse;
import net.creeperhost.minetogether.lib.web.requests.GetClosestDCRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.orderform.data.Order;
import net.creeperhost.minetogethercommunity.orderform.data.OrderSummary;
import net.creeperhost.minetogethercommunity.orderform.requests.GetCurrencyRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetDataCentresRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetLatencyRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetLocationsRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetNameAvailableRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetProductRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetRecommendRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.GetSummaryRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.PostCreateAccountRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.PostEmailExistsRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.PostLoginRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.PostOrderRequest;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderRequests {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Order");

    public static List<GetDataCentresRequest.DC> getDataCenters(int ram) {
        try {
            GetDataCentresRequest.Response response = MineTogether.API.execute(new GetDataCentresRequest(ram)).apiResponse();
            if (response == null || !"success".equals(response.getStatus())) {
                LOGGER.error("Failed to retrieve data center list. API returned: {}", response == null ? "no response" : response.getMessage());
                return Collections.emptyList();
            }
            return response.dataCentres == null ? Collections.<GetDataCentresRequest.DC>emptyList() : response.dataCentres;
        } catch (Throwable ex) {
            LOGGER.error("Failed to retrieve data center list", ex);
            return Collections.emptyList();
        }
    }

    public static void initDefaults(Order order) {
        if (StringUtils.isBlank(order.country)) {
            order.country = Countries.getOurCountry(MineTogether.API);
        }
        if (StringUtils.isBlank(order.country)) {
            order.country = "US";
        }
        if (StringUtils.isBlank(order.serverLocation)) {
            order.serverLocation = getClosestLocation();
        }
    }

    public static Map<String, Integer> getLocations() {
        try {
            GetLocationsRequest.Response response = MineTogether.API.execute(new GetLocationsRequest()).apiResponse();
            return response == null || response.locMap == null ? Collections.<String, Integer>emptyMap() : response.locMap;
        } catch (Throwable ex) {
            LOGGER.error("Unable to fetch order locations", ex);
            return Collections.emptyMap();
        }
    }

    public static String getClosestLocation() {
        try {
            GetClosestDCRequest.Response response = MineTogether.API.execute(new GetClosestDCRequest()).apiResponse();
            if (response != null && response.getDataCenter() != null && StringUtils.isNotBlank(response.getDataCenter().getName())) {
                return response.getDataCenter().getName();
            }
        } catch (Throwable ex) {
            LOGGER.warn("Unable to fetch closest order location", ex);
        }
        return "";
    }

    public static GetClosestDCRequest.Response getDCsByDistance() {
        try {
            return MineTogether.API.execute(new GetClosestDCRequest()).apiResponse();
        } catch (Throwable ex) {
            LOGGER.error("Failed to retrieve data centers by distance", ex);
            return null;
        }
    }

    public static int getDCLatency(String latencyUrl, long distance) {
        try {
            GetLatencyRequest.Response response = MineTogether.API.execute(new GetLatencyRequest(latencyUrl)).apiResponse();
            if (response == null) {
                LOGGER.debug("Failed to check data center latency. API returned: no response");
                return -1;
            }
            if (StringUtils.isNotBlank(response.getStatus()) && !"success".equals(response.getStatus())) {
                LOGGER.debug("Failed to check data center latency. API returned status: {}", response.getStatus());
                return -1;
            }

            double latency = response.latency;
            double milesPerSecond = 124188D;
            double minMs = ((distance / milesPerSecond) * 1000D) * 1.7D;
            if (latency < minMs) latency = Math.round(minMs);
            return (int) Math.max(latency, 1);
        } catch (Throwable ex) {
            LOGGER.debug("Failed to check data center latency", ex);
            return -1;
        }
    }

    public static OrderSummary getSummary(Order order) {
        return getSummary(order, "");
    }

    public static OrderSummary getSummary(Order order, String promo) {
        initDefaults(order);

        String version = ModPackInfo.getInfo().websiteID;
        if (StringUtils.isBlank(version)) {
            version = "0";
        }

        try {
            GetRecommendRequest.Response recommend = MineTogether.API.execute(new GetRecommendRequest(version, order.playerAmount)).apiResponse();
            String recommended = String.valueOf(recommend.recommended);

            if (StringUtils.isNotBlank(promo) && !"Insert Promo Code here".equalsIgnoreCase(promo)) {
                getWebResponse("https://www.creeperhost.net/applyPromo/" + promo);
            }

            GetSummaryRequest.Response summary = MineTogether.API.execute(new GetSummaryRequest(order.country, recommended)).apiResponse();
            if (summary == null || summary.option0 == null) {
                return new OrderSummary("Unable to fetch summary");
            }

            GetSummaryRequest.Option option = summary.option0;
            double discount = option.discount == null ? 0D : option.discount;
            GetCurrencyRequest.Response currency = MineTogether.API.execute(new GetCurrencyRequest(order.country)).apiResponse();
            GetProductRequest.Response product = MineTogether.API.execute(new GetProductRequest(recommended)).apiResponse();

            List<String> features = new ArrayList<>();
            if (product != null && product.description != null) {
                Matcher matcher = Pattern.compile("<li>(.*?)<").matcher(product.description);
                while (matcher.find()) {
                    features.add(matcher.group(1));
                }
            }
            List<String> included = new ArrayList<>();
            included.add("minetogether.quote.vpsincluded1");
            included.add("minetogether.quote.vpsincluded2");
            included.add("minetogether.quote.vpsincluded3");
            included.add("minetogether.quote.vpsincluded4");
            included.add("minetogether.quote.vpsincluded5");
            included.add("minetogether.quote.vpsincluded6");
            included.add("minetogether.quote.vpsincluded7");

            String displayName = product == null || product.displayName == null ? recommended : product.displayName;
            String prefix = currency == null || currency.prefix == null ? "" : currency.prefix;
            String suffix = currency == null || currency.suffix == null ? "" : currency.suffix;
            String currencyId = currency == null || currency.id == null ? "" : currency.id;
            String currencyCode = currency == null || currency.code == null ? "" : currency.code;
            order.productID = recommended;
            order.currency = currencyId;

            return new OrderSummary(recommended, displayName, features, included, option.preDiscount, option.subtotal, option.total, Math.max(option.tax, 0D), discount, suffix, prefix, currencyId, currencyCode, recommend.ram);
        } catch (Throwable ex) {
            LOGGER.error("Unable to fetch order summary", ex);
            return new OrderSummary("Unable to fetch summary");
        }
    }

    public static ApiResponse getNameAvailable(String name) {
        try {
            return MineTogether.API.execute(new GetNameAvailableRequest(name)).apiResponse();
        } catch (Throwable ex) {
            LOGGER.error("Unable to check server name availability", ex);
            return new ApiResponse("error", "Unknown Error");
        }
    }

    public static boolean doesAccountExist(String email) {
        try {
            ApiResponse response = MineTogether.API.execute(new PostEmailExistsRequest(email)).apiResponse();
            return response != null && "error".equals(response.getStatus());
        } catch (Throwable ex) {
            LOGGER.error("Unable to check whether order account exists", ex);
            return false;
        }
    }

    public static PostLoginRequest.Response doLogin(String email, String password) {
        try {
            return MineTogether.API.execute(new PostLoginRequest(email, password)).apiResponse();
        } catch (Throwable ex) {
            LOGGER.error("Unable to log in to order account", ex);
            return new PostLoginRequest.Response("error", "Unknown Error");
        }
    }

    public static PostCreateAccountRequest.Response createAccount(Order order) {
        try {
            return MineTogether.API.execute(new PostCreateAccountRequest(order)).apiResponse();
        } catch (Throwable ex) {
            LOGGER.error("Unable to create order account", ex);
            return new PostCreateAccountRequest.Response("error", "Unknown Error");
        }
    }

    public static PostOrderRequest.Response placeOrder(Order order) {
        initDefaults(order);
        if (StringUtils.isBlank(order.productID)) {
            OrderSummary summary = getSummary(order);
            if (StringUtils.isNotBlank(summary.summaryError)) {
                return new PostOrderRequest.Response("error", summary.summaryError);
            }
        }

        if (StringUtils.isBlank(order.clientID)) {
            if (doesAccountExist(order.emailAddress)) {
                PostLoginRequest.Response login = doLogin(order.emailAddress, order.password);
                if (login == null || !"success".equals(login.getStatus())) {
                    return new PostOrderRequest.Response("error", login == null ? "Login failed" : login.getMessage());
                }
                order.clientID = StringUtils.defaultIfBlank(login.userid, "0");
                order.currency = StringUtils.defaultIfBlank(login.currency, order.currency);
            } else {
                PostCreateAccountRequest.Response created = createAccount(order);
                if (created == null || !"success".equals(created.getStatus())) {
                    return new PostOrderRequest.Response("error", created == null ? "Account creation failed" : created.getMessage());
                }
                order.clientID = StringUtils.defaultIfBlank(created.userid, "0");
                order.currency = StringUtils.defaultIfBlank(created.currency, order.currency);
            }
        }

        String location = StringUtils.defaultIfBlank(order.serverLocation, getClosestLocation());
        String dcId = StringUtils.defaultIfBlank(getLocationId(location), location);
        return placeOrder(order, dcId, order.pregen ? "1" : "0", "");
    }

    public static PostOrderRequest.Response placeOrder(Order order, String dcId, String pregen, String fallbackName) {
        try {
            PostOrderRequest.Response response = MineTogether.API.execute(new PostOrderRequest(order, dcId, pregen, fallbackName)).apiResponse();
            return response == null ? new PostOrderRequest.Response("error", "Unknown Error") : response;
        } catch (Throwable ex) {
            LOGGER.error("Unable to place order", ex);
            return new PostOrderRequest.Response("error", "Unknown Error");
        }
    }

    private static String getLocationId(String location) {
        Integer id = getLocations().get(location);
        return id == null ? "" : String.valueOf(id);
    }

    private static String getWebResponse(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", WebUtils.userAgent);
        connection.setRequestProperty("Fingerprint", MineTogether.FINGERPRINT);
        connection.setRequestProperty("Identifier", ModPackInfo.getInfo().realName);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        InputStream input = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) return "";

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } finally {
            connection.disconnect();
        }
        return builder.toString();
    }
}
