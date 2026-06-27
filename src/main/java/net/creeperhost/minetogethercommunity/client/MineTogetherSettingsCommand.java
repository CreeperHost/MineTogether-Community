package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class MineTogetherSettingsCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "minetogether_settings";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/minetogether_settings";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        final Minecraft mc = Minecraft.getMinecraft();
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(() -> mc.displayGuiScreen(new SettingGui.Screen(mc.currentScreen)));
    }

}
