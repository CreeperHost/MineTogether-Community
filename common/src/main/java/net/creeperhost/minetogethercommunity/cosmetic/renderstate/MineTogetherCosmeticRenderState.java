package net.creeperhost.minetogethercommunity.cosmetic.renderstate;

public interface MineTogetherCosmeticRenderState {
    String minetogether$hatId();

    String minetogether$capeId();

    String minetogether$tailId();

    String minetogether$wingId();

    boolean minetogether$suppressVanillaCape();

    boolean minetogether$fullBright();

    void minetogether$setCosmetics(String hatId, String capeId, String tailId, String wingId, boolean suppressVanillaCape, boolean fullBright);
}
