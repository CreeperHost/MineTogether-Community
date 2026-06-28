package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Created by brandon3055 on 21/04/2023
 */
public final class RemoteServer {

    public final String friend;
    public final String serverToken;
    public final @Nullable String node;
    public final @Nullable String modpackKey;
    public final PackCompatibility compatibility;

    public Component status;
    public Component motd;
    public long ping;
    public boolean pinged;
    public List<Component> playerList = Collections.emptyList();
    public int protocol = SharedConstants.getCurrentVersion().getProtocolVersion();
    public Component version = Component.literal(SharedConstants.getCurrentVersion().getName());
    @Nullable
    private byte[] iconBytes;

    public RemoteServer(String friend, String serverToken, @Nullable String node) {
        this(friend, serverToken, node, null, PackCompatibility.UNKNOWN);
    }

    public RemoteServer(String friend, String serverToken, @Nullable String node, @Nullable String modpackKey, PackCompatibility compatibility) {
        this.friend = friend;
        this.serverToken = serverToken;
        this.node = node;
        this.modpackKey = modpackKey;
        this.compatibility = compatibility;
    }

    public static RemoteServer fromEntry(CFriendServers.ServerEntry entry) {
        return new RemoteServer(entry.friend, entry.serverToken, entry.node, readStringField(entry, "modpackKey"), readCompatibility(entry));
    }

    public boolean shouldWarnBeforeJoin() {
        return compatibility != PackCompatibility.SAME;
    }

    private static @Nullable String readStringField(Object source, String fieldName) {
        try {
            Field field = source.getClass().getField(fieldName);
            Object value = field.get(source);
            return value instanceof String string && !string.isEmpty() ? string : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static PackCompatibility readCompatibility(Object source) {
        try {
            Field field = source.getClass().getField("compatibility");
            Object value = field.get(source);
            if (value != null) {
                return PackCompatibility.valueOf(String.valueOf(value));
            }
        } catch (IllegalArgumentException | ReflectiveOperationException ignored) {
        }
        return PackCompatibility.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteServer that = (RemoteServer) o;
        return Objects.equals(friend, that.friend) && Objects.equals(serverToken, that.serverToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(friend, serverToken);
    }

    @Override
    public String toString() {
        return "RemoteServer[" +
                "friend=" + friend + ", " +
                "serverToken=" + serverToken + ']';
    }

    @Nullable
    public byte[] getIconBytes() {
        return this.iconBytes;
    }

    public void setIconBytes(@Nullable byte[] bs) {
        this.iconBytes = bs;
    }

    public enum PackCompatibility {
        SAME,
        DIFFERENT,
        UNKNOWN
    }
}
