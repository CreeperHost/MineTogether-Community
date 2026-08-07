package net.creeperhost.minetogethercommunity.ci.connectservice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AccessPolicy {

    static final int DEFAULT_MAX_PLAYERS = 8;

    private final Set<Friendship> friendships = new HashSet<>();
    private final Map<UUID, Integer> limits = new HashMap<>();

    synchronized void setFriend(UUID left, UUID right, boolean enabled) {
        Friendship friendship = new Friendship(left, right);
        if (enabled) friendships.add(friendship);
        else friendships.remove(friendship);
    }

    synchronized boolean areFriends(UUID left, UUID right) {
        return friendships.contains(new Friendship(left, right));
    }

    synchronized void setLimit(UUID user, int maxPlayers) {
        if (maxPlayers == 0 || maxPlayers < -1) {
            throw new IllegalArgumentException("maxPlayers must be -1 or positive");
        }
        limits.put(user, maxPlayers);
    }

    synchronized int limit(UUID user) {
        return limits.getOrDefault(user, DEFAULT_MAX_PLAYERS);
    }

    synchronized int friendshipCount() {
        return friendships.size();
    }

    synchronized void reset() {
        friendships.clear();
        limits.clear();
    }

    private record Friendship(UUID first, UUID second) {
        private Friendship {
            if (compare(first, second) > 0) {
                UUID swap = first;
                first = second;
                second = swap;
            }
        }

        private static int compare(UUID left, UUID right) {
            int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
            return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
        }
    }
}
