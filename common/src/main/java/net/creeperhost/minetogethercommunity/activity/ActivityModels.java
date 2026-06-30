package net.creeperhost.minetogethercommunity.activity;

import java.util.ArrayList;
import java.util.List;

public class ActivityModels {

    public static class Batch {
        public String clientSessionId;
        public long sequence;
        public long sentAt;
        public Modpack modpack;
        public World world;
        public Playtime playtime;
        public List<Metadata> metadata = new ArrayList<>();
        public List<AdvancementEvent> advancements = new ArrayList<>();
        public List<QuestEvent> questCompletions = new ArrayList<>();
    }

    public static class Modpack {
        public String source = "unknown";
        public String packId = "";
        public String versionId = "";
        public String websiteId = "";
        public String minecraftVersion = "";
        public String loader = "";
        public String modVersion = "";
    }

    public static class World {
        public String kind = "unknown";
        public String key = "";
    }

    public static class Playtime {
        public long from;
        public long to;
        public int deltaSeconds;
    }

    public static class Metadata {
        public String metadataRef = "";
        public String type = "";
        public String provider = "";
        public String contentId = "";
        public String locale = "en_us";
        public String titleKey = "";
        public String descriptionKey = "";
        public String titleEn = "";
        public String descriptionEn = "";
        public String titleComponentJson = "";
        public String descriptionComponentJson = "";
        public String iconItemId = "";
        public String frameOrType = "";
    }

    public static class AdvancementEvent {
        public String eventId = "";
        public String metadataRef = "";
        public long completedAt;
        public String source = "";
    }

    public static class QuestEvent {
        public String eventId = "";
        public String metadataRef = "";
        public String provider = "";
        public long completedAt;
        public String source = "";
    }

    public static class QueueState {
        public String clientSessionId = "";
        public String authKey = "";
        public long nextSequence = 1;
        public List<Batch> pending = new ArrayList<>();
    }
}
