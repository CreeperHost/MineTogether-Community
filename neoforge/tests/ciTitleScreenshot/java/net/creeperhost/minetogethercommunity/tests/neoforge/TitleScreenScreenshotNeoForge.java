package net.creeperhost.minetogethercommunity.tests.neoforge;

import net.creeperhost.minetogethercommunity.tests.TitleScreenScreenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "minetogether_title_screenshot", dist = Dist.CLIENT)
public final class TitleScreenScreenshotNeoForge {
    public TitleScreenScreenshotNeoForge() {
        var test = new TitleScreenScreenshot();
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> test.afterFrame());
    }
}
