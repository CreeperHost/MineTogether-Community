package net.creeperhost.minetogethercommunity.forge.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.data.event.GatherDataEvent;

/**
 * Created by brandon3055 on 02/02/2025
 */
@Mod.EventBusSubscriber (bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenEventHandler {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();

        gen.addProvider(true, new LangGenerator(gen.getPackOutput()));
    }
}
