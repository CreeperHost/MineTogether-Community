package net.creeperhost.minetogethercommunity.neoforge.datagen;

import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Created by brandon3055 on 02/02/2025
 */
public class DataGenEventHandler {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(DataGenEventHandler::gatherData);
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        DataGenerator gen = event.getGenerator();

        gen.addProvider(true, new LangGenerator(gen.getPackOutput()));
    }
}
