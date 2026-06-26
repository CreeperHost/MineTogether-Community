package net.creeperhost.minetogethercommunity.oauth;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class OAuthWebServer extends NanoHTTPD {

    private BiConsumer<String, String> codeHandler;

    public OAuthWebServer(boolean daemon, int port) throws IOException {
        super(port);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, daemon);
    }

    public void setCodeHandler(BiConsumer<String, String> handler) {
        codeHandler = handler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, List<String>> params = session.getParameters();
        String location = "https://minetogether.io/wut";
        String code = first(params, "code");
        if (code != null && codeHandler != null) {
            codeHandler.accept(code, first(params, "state"));
            location = "https://minetogether.io/modloggedin";
        }

        Response response = newFixedLengthResponse("MineTogether login complete. You can close this tab.");
        response.addHeader("Location", location);
        response.setStatus(Response.Status.REDIRECT);
        return response;
    }

    private static String first(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
