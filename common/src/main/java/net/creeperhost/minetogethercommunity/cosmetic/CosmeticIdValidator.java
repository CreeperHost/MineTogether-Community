package net.creeperhost.minetogethercommunity.cosmetic;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/** Server-safe validation for cosmetic IDs received over the network. */
public final class CosmeticIdValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}", Pattern.CASE_INSENSITIVE);

    public static boolean isValid(@Nullable String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private CosmeticIdValidator() {
    }
}
