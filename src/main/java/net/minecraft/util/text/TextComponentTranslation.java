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
        TextComponentCompat.siblings(this).add(new TextComponentString(text));
        return this;
    }

    public ITextComponent appendSibling(ITextComponent component) {
        TextComponentCompat.siblings(this).add(component);
        return this;
    }

    public ITextComponent appendSibling(IChatComponent component) {
        TextComponentCompat.siblings(this).add(component);
        return this;
    }

    public String getUnformattedComponentText() {
        return getUnformattedTextForChat();
    }

    @SuppressWarnings("unchecked")
    public List<ITextComponent> getSiblings() {
        return (List<ITextComponent>) (List<?>) TextComponentCompat.siblings(this);
    }

    public TextComponentTranslation createCopy() {
        TextComponentTranslation copy = new TextComponentTranslation(getKey(), getFormatArgs());
        copy.setChatStyle(getChatStyle().createDeepCopy());
        for (Object sibling : TextComponentCompat.siblings(this)) {
            copy.appendSibling(((IChatComponent) sibling).createCopy());
        }
        return copy;
    }
}
