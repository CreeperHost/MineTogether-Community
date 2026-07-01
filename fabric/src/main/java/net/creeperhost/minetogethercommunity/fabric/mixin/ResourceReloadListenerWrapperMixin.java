package net.creeperhost.minetogethercommunity.fabric.mixin;

import net.creeperhost.polylib.fabric.client.ResourceReloadListenerWrapper;
import net.creeperhost.polylib.client.modulargui.sprite.ModAtlasHolder;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(value = ResourceReloadListenerWrapper.class, remap = false)
public class ResourceReloadListenerWrapperMixin {
    @Shadow @Final private Supplier<ModAtlasHolder> getWrapped;

    public CompletableFuture<Void> reload(
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager manager,
            ProfilerFiller preparationsProfiler,
            ProfilerFiller reloadProfiler,
            Executor backgroundExecutor,
            Executor gameExecutor
    ) {
        return getWrapped.get().reload(barrier, manager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
    }
}
