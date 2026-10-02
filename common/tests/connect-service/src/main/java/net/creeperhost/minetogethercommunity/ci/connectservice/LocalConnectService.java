package net.creeperhost.minetogethercommunity.ci.connectservice;

import net.creeperhost.minetogether.connect.lib.util.RSAUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class LocalConnectService {

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        if (options.help) {
            System.out.println("Usage: java -jar minetogether-connect-service.jar "
                    + "[--bind 127.0.0.1] [--proxy-port 0] [--control-port 0] [--work-dir PATH]");
            return;
        }

        Files.createDirectories(options.workDirectory);
        KeyMaterial keys = KeyMaterial.loadOrCreate(options.workDirectory);
        ConnectRegistry registry = new ConnectRegistry();
        CountDownLatch shutdown = new CountDownLatch(1);

        try (ProxyServer proxy = new ProxyServer(options.bindAddress, options.proxyPort, keys.privateKey(), registry);
             ControlServer control = new ControlServer(options.bindAddress, options.controlPort, registry, shutdown::countDown)) {
            int proxyPort = proxy.start();
            int controlPort = control.start();
            Path nodeFile = options.workDirectory.resolve("connect-nodes.json").toAbsolutePath().normalize();
            writeNodeFile(nodeFile, options.bindAddress, proxyPort, keys);

            Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "MTConnect CI shutdown"));
            Map<String, Object> ready = new LinkedHashMap<>();
            ready.put("proxy", options.bindAddress + ":" + proxyPort);
            ready.put("control", "http://" + options.bindAddress + ":" + controlPort);
            ready.put("nodeFile", nodeFile.toString());
            System.out.println("READY " + Json.value(ready));
            System.out.flush();
            shutdown.await();
        }
    }

    private static void writeNodeFile(Path path, String address, int port, KeyMaterial keys) throws IOException {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", "ci-local");
        node.put("location", "ci-local");
        node.put("address", address);
        node.put("port", port);
        node.put("publicKey", RSAUtils.encodeRSAKey(keys.publicKey()));
        Files.writeString(path, Json.value(List.of(node)) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static final class Options {
        private String bindAddress = "127.0.0.1";
        private int proxyPort;
        private int controlPort;
        private Path workDirectory = Paths.get("build", "ci-connect-service");
        private boolean help;

        private static Options parse(String[] arguments) {
            Options options = new Options();
            List<String> args = new ArrayList<>(List.of(arguments));
            for (int i = 0; i < args.size(); i++) {
                String argument = args.get(i);
                switch (argument) {
                    case "--help", "-h" -> options.help = true;
                    case "--bind" -> options.bindAddress = value(args, ++i, argument);
                    case "--proxy-port" -> options.proxyPort = port(value(args, ++i, argument), argument);
                    case "--control-port" -> options.controlPort = port(value(args, ++i, argument), argument);
                    case "--work-dir" -> options.workDirectory = Paths.get(value(args, ++i, argument));
                    default -> throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }
            return options;
        }

        private static String value(List<String> arguments, int index, String option) {
            if (index >= arguments.size()) throw new IllegalArgumentException("Missing value for " + option);
            return arguments.get(index);
        }

        private static int port(String value, String option) {
            try {
                int port = Integer.parseInt(value);
                if (port < 0 || port > 65535) throw new NumberFormatException();
                return port;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(option + " must be a port from 0 to 65535");
            }
        }
    }

    private LocalConnectService() {
    }
}
