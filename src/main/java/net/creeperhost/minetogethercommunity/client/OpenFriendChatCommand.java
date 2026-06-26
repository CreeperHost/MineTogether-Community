package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class OpenFriendChatCommand extends CommandBase {

    @Override
    public String getName() {
        return "minetogether_friend_chat";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/minetogether_friend_chat <profileHash>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
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
