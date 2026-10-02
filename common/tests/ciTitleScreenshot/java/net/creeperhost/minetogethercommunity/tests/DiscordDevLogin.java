package net.creeperhost.minetogethercommunity.tests;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Wraps each loader's login entry point so DevLogin can report before Minecraft loads any mods. */
public final class DiscordDevLogin {
    public static void main(String[] args) {
        PrintStream console = System.out;
        try {
            // Never allow DevLogin's diagnostic option to print access tokens into CI logs.
            System.clearProperty("devlogin.yes_i_really_just_want_to_dump_to_console");
            var reporter = DiscordReporter.fromEnvironment();
            if (reporter.isPresent()) {
                System.setOut(new LoginConsole(console, message -> reporter.get().send(message, List.of())));
            }
            Class.forName(System.getProperty("minetogether.ci.launcher")).getMethod("main", String[].class)
                    .invoke(null, (Object) args);
        } catch (Throwable error) {
            // Launcher/auth exceptions may contain credentials. Do not dump arguments or causes.
            console.println("CI client launch failed. If sign-in expired, rerun the workflow for a new code.");
            System.exit(1);
        } finally {
            System.setOut(console);
        }
    }

    @FunctionalInterface
    interface Report {
        void send(String message) throws IOException;
    }

    /** DevLogin 0.1.0.5 prints the Microsoft device message with println(String). */
    static final class LoginConsole extends PrintStream {
        private static final Pattern DEVICE_MESSAGE = Pattern.compile(
                "\\[DevLogin]:\\s*To sign in, use a web browser to open the page "
                        + "(https://(?:www\\.microsoft\\.com/link|microsoft\\.com/devicelogin)) "
                        + "and enter the code ([A-Z0-9-]{6,20}) to authenticate\\.");
        private final PrintStream console;
        private final Report report;
        private final Set<String> sent = new HashSet<>();

        LoginConsole(PrintStream console, Report report) {
            super(console, true);
            this.console = console;
            this.report = report;
        }

        @Override
        public synchronized void println(String line) {
            if (line != null && line.startsWith("[DevLogin]:")) {
                var match = DEVICE_MESSAGE.matcher(line);
                if (!match.matches()) {
                    // Fail explicitly if upstream changes its format, without publishing the raw prompt.
                    console.println("[DevLogin] Device prompt format changed; update the CI sign-in reporter.");
                    throw new IllegalStateException("Unrecognized DevLogin device prompt.");
                }
                String code = match.group(2);
                if (sent.add(code)) {
                    try {
                        report.send("Minecraft sign-in needed for the MineTogether test run.\n"
                                + "Open " + match.group(1) + " and enter code **" + code + "**.\n"
                                + "The run will continue automatically after approval. If this code expires, "
                                + "start the manual workflow again for a fresh one.");
                    } catch (IOException error) {
                        console.println("[DevLogin] Could not send the sign-in request to Discord.");
                        throw new UncheckedIOException(new IOException("Could not send the sign-in request to Discord."));
                    }
                    console.println("[DevLogin] Sign-in link and code sent to Discord; waiting for approval.");
                }
                return; // Device codes stay out of the workflow log and uploaded launch.log.
            }
            console.println(line);
        }
    }
}
