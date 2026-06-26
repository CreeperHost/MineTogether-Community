package net.minecraft.util.text;

import net.minecraft.util.EnumChatFormatting;

public enum TextFormatting {
    BLACK(EnumChatFormatting.BLACK),
    DARK_BLUE(EnumChatFormatting.DARK_BLUE),
    DARK_GREEN(EnumChatFormatting.DARK_GREEN),
    DARK_AQUA(EnumChatFormatting.DARK_AQUA),
    DARK_RED(EnumChatFormatting.DARK_RED),
    DARK_PURPLE(EnumChatFormatting.DARK_PURPLE),
    GOLD(EnumChatFormatting.GOLD),
    GRAY(EnumChatFormatting.GRAY),
    DARK_GRAY(EnumChatFormatting.DARK_GRAY),
    BLUE(EnumChatFormatting.BLUE),
    GREEN(EnumChatFormatting.GREEN),
    AQUA(EnumChatFormatting.AQUA),
    RED(EnumChatFormatting.RED),
    LIGHT_PURPLE(EnumChatFormatting.LIGHT_PURPLE),
    YELLOW(EnumChatFormatting.YELLOW),
    WHITE(EnumChatFormatting.WHITE),
    OBFUSCATED(EnumChatFormatting.OBFUSCATED),
    BOLD(EnumChatFormatting.BOLD),
    STRIKETHROUGH(EnumChatFormatting.STRIKETHROUGH),
    UNDERLINE(EnumChatFormatting.UNDERLINE),
    ITALIC(EnumChatFormatting.ITALIC),
    RESET(EnumChatFormatting.RESET);

    private final EnumChatFormatting legacy;

    TextFormatting(EnumChatFormatting legacy) {
        this.legacy = legacy;
    }

    public EnumChatFormatting unwrap() {
        return legacy;
    }

    public String toString() {
        return legacy.toString();
    }

    public static String getTextWithoutFormattingCodes(String value) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(value);
    }
}
