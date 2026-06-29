package net.creeperhost.minetogethercommunity.chat.gui;

import com.mojang.authlib.GameProfile;
import net.creeperhost.minetogethercommunity.gui.MTTextures;
import net.creeperhost.polylib.client.modulargui.elements.GuiElement;
import net.creeperhost.polylib.client.modulargui.lib.BackgroundRender;
import net.creeperhost.polylib.client.modulargui.lib.GuiRender;
import net.creeperhost.polylib.client.modulargui.lib.geometry.GuiParent;
import net.creeperhost.polylib.client.modulargui.sprite.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by brandon3055 on 28/10/2023
 */
public class PlayerIconElement extends GuiElement<PlayerIconElement> implements BackgroundRender {
    @Nullable
    private GameProfile profile;
    private final Material fallback = MTTextures.get("player_offline");
    public boolean textureFail = false;

    /**
     * @param parent parent {@link GuiParent}.
     */
    public PlayerIconElement(@NotNull GuiParent<?> parent, @Nullable GameProfile profile) {
        super(parent);
        this.profile = profile;
    }

    public void setProfile(GameProfile profile) {
        this.profile = profile;
    }

    @Override
    public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
        render.texRect(fallback, getRectangle());
    }
}
