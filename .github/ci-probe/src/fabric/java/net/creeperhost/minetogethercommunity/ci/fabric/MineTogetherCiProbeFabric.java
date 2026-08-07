package net.creeperhost.minetogethercommunity.ci.fabric;

import net.creeperhost.minetogethercommunity.ci.MineTogetherCiProbe;
import net.fabricmc.api.ClientModInitializer;

public final class MineTogetherCiProbeFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MineTogetherCiProbe.init();
    }
}
