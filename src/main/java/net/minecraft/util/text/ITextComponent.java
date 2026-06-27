package net.minecraft.util.text;

import net.minecraft.util.IChatComponent;

import java.util.List;

public interface ITextComponent extends IChatComponent {
    Style getStyle();

    ITextComponent setStyle(Style style);

    ITextComponent appendText(String text);

    ITextComponent appendSibling(ITextComponent component);

    String getUnformattedComponentText();

    ITextComponent createCopy();

    List<IChatComponent> getSiblings();

    final class Serializer {
        private Serializer() {
        }

        public static String componentToJson(ITextComponent component) {
            if (component == null) return "";
            return "{\"text\":\"" + escape(component.getUnformattedText()) + "\"}";
        }

        private static String escape(String value) {
            if (value == null) return "";
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
