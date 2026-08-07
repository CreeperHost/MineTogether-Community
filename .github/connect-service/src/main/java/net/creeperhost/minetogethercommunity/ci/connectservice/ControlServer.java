package net.creeperhost.minetogethercommunity.ci.connectservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ControlServer implements AutoCloseable {

    private final String bindAddress;
    private final int requestedPort;
    private final ConnectRegistry registry;
    private final Runnable shutdown;
    private HttpServer server;
    private ExecutorService executor;

    ControlServer(String bindAddress, int requestedPort, ConnectRegistry registry, Runnable shutdown) {
        this.bindAddress = bindAddress;
        this.requestedPort = requestedPort;
        this.registry = registry;
        this.shutdown = shutdown;
    }

    int start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, requestedPort), 0);
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "MTConnect CI control");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/state", exchange -> get(exchange, registry.stateJson()));
        server.createContext("/events", exchange -> get(exchange, registry.eventsJson()));
        server.createContext("/fixture/reset", exchange -> post(exchange, () -> {
            registry.reset();
            return registry.stateJson();
        }));
        server.createContext("/fixture/friend", exchange -> post(exchange, () -> {
            Map<String, String> query = query(exchange.getRequestURI());
            registry.setFriend(uuid(query, "left"), uuid(query, "right"), bool(query, "enabled", true));
            return registry.stateJson();
        }));
        server.createContext("/fixture/limit", exchange -> post(exchange, () -> {
            Map<String, String> query = query(exchange.getRequestURI());
            registry.setLimit(uuid(query, "user"), integer(query, "max"));
            return registry.stateJson();
        }));
        server.createContext("/fault/drop-host", exchange -> post(exchange, () -> {
            Map<String, String> query = query(exchange.getRequestURI());
            boolean dropped = registry.dropHost(uuid(query, "user"));
            return "{\"dropped\":" + dropped + "}";
        }));
        server.createContext("/shutdown", this::shutdown);
        server.start();
        return server.getAddress().getPort();
    }

    private static void get(HttpExchange exchange, String body) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"GET required\"}");
            return;
        }
        respond(exchange, 200, body);
    }

    private static void post(HttpExchange exchange, Action action) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }
        try {
            respond(exchange, 200, action.run());
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "{\"error\":" + Json.string(exception.getMessage()) + "}");
        } catch (RuntimeException exception) {
            respond(exchange, 500, "{\"error\":" + Json.string(exception.toString()) + "}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void shutdown(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }
        respond(exchange, 200, "{\"status\":\"stopping\"}");
        shutdown.run();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) return values;
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private static UUID uuid(Map<String, String> query, String name) {
        String value = required(query, name);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a UUID");
        }
    }

    private static int integer(Map<String, String> query, String name) {
        try {
            return Integer.parseInt(required(query, name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    private static boolean bool(Map<String, String> query, String name, boolean fallback) {
        String value = query.get(name);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static String required(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing query parameter: " + name);
        return value;
    }

    @Override
    public void close() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @FunctionalInterface
    private interface Action {
        String run();
    }
}
