package net.creeperhost.minetogethercommunity.tests;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DiscordDevLoginTest {
    private static String prompt(String code) {
        return "[DevLogin]: To sign in, use a web browser to open the page https://www.microsoft.com/link "
                + "and enter the code " + code + " to authenticate.";
    }

    @Test
    void sendsInitialAndReplacementCodesWithoutLeakingThemIntoLogs() {
        var output = new ByteArrayOutputStream();
        var messages = new ArrayList<String>();
        var console = new DiscordDevLogin.LoginConsole(new PrintStream(output), messages::add);
        console.println(prompt("ABCD12345"));
        console.println(prompt("ABCD12345"));
        console.println("[DevLogin] Account grant expired. Must login again.");
        console.println(prompt("NEWCODE12"));
        assertEquals(2, messages.size());
        assertTrue(messages.getFirst().contains("https://www.microsoft.com/link"));
        assertTrue(messages.getFirst().contains("**ABCD12345**"));
        assertTrue(messages.getLast().contains("**NEWCODE12**"));
        assertFalse(output.toString().contains("ABCD12345"));
        assertFalse(output.toString().contains("NEWCODE12"));
        assertTrue(output.toString().contains("waiting for approval"));
    }

    @Test
    void cachedLoginDoesNotSendAnUnnecessaryPrompt() {
        var output = new ByteArrayOutputStream();
        var messages = new ArrayList<String>();
        var console = new DiscordDevLogin.LoginConsole(new PrintStream(output), messages::add);
        console.println("[DevLogin] Validating profile: minetogether-ci");
        console.println("[DevLogin] Refreshing Microsoft auth.");
        console.println("[DevLogin] Logged into Minecraft!");
        assertTrue(messages.isEmpty());
        assertTrue(output.toString().contains("Logged into Minecraft!"));
    }

    @Test
    void rejectsUnexpectedPromptWithoutForwardingOrLoggingSecrets() {
        var output = new ByteArrayOutputStream();
        var messages = new ArrayList<String>();
        var console = new DiscordDevLogin.LoginConsole(new PrintStream(output), messages::add);
        assertThrows(IllegalStateException.class,
                () -> console.println("[DevLogin]: Unexpected SECRET_TOKEN"));
        assertThrows(IllegalStateException.class,
                () -> console.println(prompt("ABCD12345").replace("www.microsoft.com", "example.org")));
        assertTrue(messages.isEmpty());
        assertFalse(output.toString().contains("SECRET_TOKEN"));
        assertFalse(output.toString().contains("ABCD12345"));
        assertTrue(output.toString().contains("Device prompt format changed"));
    }

    @Test
    void webhookFailureFailsLaunchWithSanitizedError() {
        var output = new ByteArrayOutputStream();
        var console = new DiscordDevLogin.LoginConsole(new PrintStream(output), message -> {
            throw new IOException("SECRET_WEBHOOK");
        });
        var error = assertThrows(UncheckedIOException.class, () -> console.println(prompt("ABCD12345")));
        assertFalse(error.toString().contains("SECRET_WEBHOOK"));
        assertNull(error.getCause().getCause());
        assertFalse(output.toString().contains("SECRET_WEBHOOK"));
        assertFalse(output.toString().contains("ABCD12345"));
        assertTrue(output.toString().contains("Could not send the sign-in request"));
    }
}
