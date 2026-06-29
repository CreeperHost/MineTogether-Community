package net.minecraft.util.text;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.util.List;

public class TextComponentTranslation extends ChatComponentTranslation implements ITextComponent {
    public TextComponentTranslation(String key, Object... args) {
        super(key, args);
    }

    public Style getStyle() {
        if (getChatStyle() instanceof Style) {
            return (Style) getChatStyle();
        }
        Style style = new Style();
        style.setParentStyle(getChatStyle());
        setChatStyle(style);
        return style;
    }

    public ITextComponent setStyle(Style style) {
        setChatStyle(style);
        return this;
    }

    public ITextComponent appendText(String text) {
        super.appendSibling(new TextComponentString(text));
        return this;
    }

    public ITextComponent appendSibling(ITextComponent component) {
        super.appendSibling(component);
        return this;
    }

    public ITextComponent appendSibling(IChatComponent component) {
        super.appendSibling(component);
        return this;
    }

    public String getUnformattedComponentText() {
        return getUnformattedTextForChat();
    }

    public List<IChatComponent> getSiblings() {
        return super.getSiblings();
    }

    public TextComponentTranslation createCopy() {
        TextComponentTranslation copy = new TextComponentTranslation(getKey(), getFormatArgs());
        copy.setChatStyle(getChatStyle().createDeepCopy());
        for (Object sibling : super.getSiblings()) {
            copy.appendSibling(((IChatComponent) sibling).createCopy());
        }
        return copy;
    }
}
