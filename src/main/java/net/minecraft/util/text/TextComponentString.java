package net.minecraft.util.text;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.List;

public class TextComponentString extends ChatComponentText implements ITextComponent {
    public TextComponentString(String text) {
        super(text);
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
        super.getSiblings().add(new TextComponentString(text));
        return this;
    }

    public ITextComponent appendSibling(ITextComponent component) {
        super.getSiblings().add(component);
        return this;
    }

    public ITextComponent appendSibling(IChatComponent component) {
        super.getSiblings().add(component);
        return this;
    }

    public String getUnformattedComponentText() {
        return getChatComponentText_TextValue();
    }

    @SuppressWarnings("unchecked")
    public List<ITextComponent> getSiblings() {
        return super.getSiblings();
    }

    public TextComponentString createCopy() {
        TextComponentString copy = new TextComponentString(getChatComponentText_TextValue());
        copy.setChatStyle(getChatStyle().createDeepCopy());
        for (Object sibling : super.getSiblings()) {
            copy.appendSibling(((IChatComponent) sibling).createCopy());
        }
        return copy;
    }
}
