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

    public String func_71517_b() {
        return getCommandName();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/minetogether_settings";
    }

    public String func_71518_a(ICommandSender sender) {
        return getCommandUsage(sender);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.displayGuiScreen(new SettingGui.Screen(mc.currentScreen)));
    }

    public void func_71515_b(ICommandSender sender, String[] args) {
        processCommand(sender, args);
    }
}
