package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class MineTogetherSettingsCommand extends CommandBase {

    @Override
    public String getName() {
        return "minetogether_settings";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/minetogether_settings";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.displayGuiScreen(new SettingGui.Screen(mc.currentScreen)));
    }
}
