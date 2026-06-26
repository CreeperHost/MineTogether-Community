package net.creeperhost.minetogethercommunity.modulargui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ItemSelectDialog<E> extends GuiElement<ItemSelectDialog<E>> {

    private static final int MODAL_WIDTH = 150;
    private static final int MODAL_HEIGHT = 200;
    private static final int PADDING = 5;
    private static final int ROW_HEIGHT = 14;
    private static final int BUTTON_HEIGHT = 12;
    private static final int SEARCH_HEIGHT = 14;
    private static final int TITLE_HEIGHT = 10;

    private final Supplier<String> title;
    private final List<E> values = new ArrayList<E>();
    private final GuiTextField searchField;
    private Function<E, String> label = new Function<E, String>() {
        @Override
        public String apply(E value) {
            return value == null ? "" : String.valueOf(value);
        }
    };
    private Consumer<E> selected = new Consumer<E>() {
        @Override
        public void accept(E value) {
        }
    };
    private boolean closeOnOutsideClick;
    private int scroll;
    private E selectedValue;

    public ItemSelectDialog(GuiElement<?> parent, Supplier<String> title, List<E> values) {
        super(parent);
        this.title = title == null ? new Supplier<String>() {
            @Override
            public String get() {
                return "";
            }
        } : title;
        if (values != null) this.values.addAll(values);
        int modalLeft = parent.x() + (parent.width() - MODAL_WIDTH) / 2;
        int modalTop = parent.y() + (parent.height() - MODAL_HEIGHT) / 2;
        setBounds(modalLeft, modalTop, MODAL_WIDTH, MODAL_HEIGHT);
        searchField = new GuiTextField(this)
                .setMaxLength(64)
                .setSuggestion(I18n.format("minetogether.gui.select_dialog.search"))
                .onChanged(new Consumer<String>() {
                    @Override
                    public void accept(String value) {
                        scroll = 0;
                    }
                })
                .setBounds(x + PADDING, searchTop() + 3, width - PADDING * 2, 8);
        init();
    }

    public ItemSelectDialog(GuiElement<?> parent, Supplier<String> title, List<E> values, E defaultItem) {
        this(parent, title, values);
        this.selectedValue = defaultItem;
    }

    public ItemSelectDialog<E> setLabel(Function<E, String> label) {
        if (label != null) this.label = label;
        return this;
    }

    public ItemSelectDialog<E> setOnItemSelected(Consumer<E> selected) {
        if (selected != null) this.selected = selected;
        return this;
    }

    public ItemSelectDialog<E> setCloseOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        drawTooltipBackground(x, y, width, height);
        drawCenteredString(font(), trim(title.get(), width - PADDING * 2), x + width / 2, y + PADDING, MTStyle.Flat.TEXT_WARN);

        int listLeft = x + PADDING;
        int listTop = listTop();
        int listWidth = width - PADDING * 2;
        int listHeight = listHeight();
        List<E> filtered = filteredValues();
        GuiClip.push(listLeft, listTop, listWidth, listHeight);
        try {
            int rowY = listTop - scroll;
            for (E value : filtered) {
                if (rowY + ROW_HEIGHT >= listTop && rowY <= listTop + listHeight) {
                    boolean hover = mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                    boolean isSelected = value == selectedValue || (value != null && value.equals(selectedValue));
                    drawRect(listLeft, rowY, listLeft + listWidth, rowY + ROW_HEIGHT, hover || isSelected ? 0xA0808080 : 0xA0202020);
                    font().drawStringWithShadow(trim(label.apply(value), listWidth - 8), listLeft + 4, rowY + 3, MTStyle.Flat.TEXT);
                }
                rowY += ROW_HEIGHT;
            }
        } finally {
            GuiClip.pop();
        }

        drawRect(x + PADDING, searchTop(), x + width - PADDING, searchTop() + SEARCH_HEIGHT, 0xA0202020);
        drawScrollBar(filtered, mouseX, mouseY);
        int buttonWidth = (width - PADDING * 2 - 2) / 2;
        drawButton(mouseX, mouseY, x + PADDING, buttonTop(), buttonWidth,
                I18n.format("minetogether.gui.select_dialog.select"), selectedValue != null);
        drawButton(mouseX, mouseY, x + PADDING + buttonWidth + 2, buttonTop(), buttonWidth,
                I18n.format("minetogether.gui.select_dialog.cancel"), true);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || mouseButton != 0) return false;
        if (!modalBox().contains(mouseX, mouseY)) {
            if (closeOnOutsideClick) close();
            return true;
        }
        if (searchField.mouseClicked(mouseX, mouseY, mouseButton)) return true;

        int buttonWidth = (width - PADDING * 2 - 2) / 2;
        if (buttonHover(mouseX, mouseY, x + PADDING, buttonTop(), buttonWidth)) {
            accept();
            return true;
        }
        if (buttonHover(mouseX, mouseY, x + PADDING + buttonWidth + 2, buttonTop(), buttonWidth)) {
            close();
            return true;
        }

        HitBox list = listBox();
        if (list.contains(mouseX, mouseY)) {
            List<E> filtered = filteredValues();
            int index = (mouseY - list.y + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < filtered.size()) {
                E value = filtered.get(index);
                if (value != null && value.equals(selectedValue)) {
                    accept();
                } else {
                    selectedValue = value;
                }
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
        if (!isVisible() || !modalBox().contains(mouseX, mouseY) || dWheel == 0) return false;
        int max = maxScroll();
        if (max <= 0) return true;
        scroll = Math.max(0, Math.min(max, scroll + (dWheel < 0 ? ROW_HEIGHT * 2 : -ROW_HEIGHT * 2)));
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) throws IOException {
        if (!isVisible()) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        if (searchField.keyTyped(typedChar, keyCode)) return true;
        return true;
    }

    private void drawScrollBar(List<E> filtered, int mouseX, int mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int barX = x + width - PADDING - 4;
        int barY = listTop() + 1;
        int barHeight = listHeight() - 2;
        int contentHeight = filtered.size() * ROW_HEIGHT;
        int handleHeight = Math.max(12, barHeight * listHeight() / contentHeight);
        int handleTop = barY + (barHeight - handleHeight) * scroll / max;
        boolean hover = mouseX >= barX - 1 && mouseX < barX + 5 && mouseY >= barY && mouseY < barY + barHeight;
        drawRect(barX, barY, barX + 4, barY + barHeight, hover ? 0x80505050 : 0x20505050);
        drawRect(barX, handleTop, barX + 4, handleTop + handleHeight, hover ? 0xFFFFFFFF : 0x88FFFFFF);
    }

    private int maxScroll() {
        return Math.max(0, filteredValues().size() * ROW_HEIGHT - listBox().height);
    }

    private HitBox modalBox() {
        return new HitBox(x, y, width, height);
    }

    private HitBox listBox() {
        return new HitBox(x + PADDING, listTop(), width - PADDING * 2, listHeight());
    }

    private int listTop() {
        return y + PADDING + TITLE_HEIGHT + 3;
    }

    private int searchTop() {
        return buttonTop() - 2 - SEARCH_HEIGHT;
    }

    private int buttonTop() {
        return y + height - PADDING - BUTTON_HEIGHT;
    }

    private int listHeight() {
        return Math.max(1, searchTop() - 2 - listTop());
    }

    private void accept() {
        if (selectedValue != null) {
            selected.accept(selectedValue);
            close();
        }
    }

    private void close() {
        setVisible(false);
        setEnabled(false);
        if (parent != null) parent.remove(this);
    }

    private List<E> filteredValues() {
        String search = searchField == null ? "" : searchField.getText();
        String searchLower = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<E> filtered = new ArrayList<E>();
        for (E value : values) {
            String text = label.apply(value);
            if (searchLower.isEmpty() || (text != null && text.toLowerCase(Locale.ROOT).contains(searchLower))) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private String trim(String value, int maxWidth) {
        if (value == null) return "";
        if (font().getStringWidth(value) <= maxWidth) return value;
        int dots = font().getStringWidth("...");
        return font().trimStringToWidth(value, Math.max(1, maxWidth - dots)) + "...";
    }

    private void drawButton(int mouseX, int mouseY, int bx, int by, int bw, String label, boolean enabled) {
        boolean hover = enabled && buttonHover(mouseX, mouseY, bx, by, bw);
        int color = enabled ? (hover ? MTStyle.Flat.BUTTON_HOVER : MTStyle.Flat.BUTTON) : MTStyle.Flat.BUTTON_DISABLED;
        drawRect(bx, by, bx + bw, by + BUTTON_HEIGHT, color);
        drawCenteredString(font(), trim(label, bw - 4), bx + bw / 2, by + 2, enabled ? MTStyle.Flat.TEXT : MTStyle.Flat.TEXT_DISABLED);
    }

    private boolean buttonHover(int mouseX, int mouseY, int bx, int by, int bw) {
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BUTTON_HEIGHT;
    }

    private void drawTooltipBackground(int left, int top, int modalWidth, int modalHeight) {
        drawRect(left, top, left + modalWidth, top + modalHeight, 0xF0101010);
        drawRect(left, top, left + modalWidth, top + 1, 0x88505050);
        drawRect(left, top + modalHeight - 1, left + modalWidth, top + modalHeight, 0x55202020);
        drawRect(left, top, left + 1, top + modalHeight, 0x88505050);
        drawRect(left + modalWidth - 1, top, left + modalWidth, top + modalHeight, 0x55202020);
    }

    private static class HitBox {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private HitBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
