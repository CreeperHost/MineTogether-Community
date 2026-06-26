package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class OpenFriendChatCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "minetogether_friend_chat";
    }

    public String func_71517_b() {
        return getCommandName();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/minetogether_friend_chat <profileHash>";
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
        if (args.length == 0 || MineTogetherChat.CHAT_STATE == null) return;
        final String hash = args[0];
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            Profile target = findFriend(hash);
            if (target == null) return;
            FriendChatGui.setSelected(target);
            mc.displayGuiScreen(new FriendChatGui.Screen(mc.currentScreen));
        });
    }

    public void func_71515_b(ICommandSender sender, String[] args) {
        processCommand(sender, args);
    }

    private Profile findFriend(String hash) {
        ProfileManager manager = MineTogetherChat.CHAT_STATE.profileManager;
        for (Profile profile : manager.getKnownProfiles()) {
            if (profile.hasFullHash() && hash.equals(profile.getFullHash()) && profile.isFriend()) {
                return profile;
            }
        }
        Profile profile = manager.lookupProfile(hash);
        return profile != null && profile.isFriend() ? profile : null;
    }
}
