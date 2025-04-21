package net.creeperhost.minetogethercommunity.gui;

import net.minecraft.network.chat.HoverEvent;

/**
 * Created by brandon3055 on 20/04/2025
 */
public class ShowPreviewHoverEvent implements HoverEvent {

    private final String url;

    public ShowPreviewHoverEvent(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public Action action() {
        return Action.SHOW_TEXT;
    }
}
