package net.creeperhost.minetogethercommunity.cosmetic.tail;

public class TailElement {

    private final String name;
    private final float[] from;
    private final float[] to;
    private final TailFace north;
    private final TailFace south;
    private final TailFace east;
    private final TailFace west;
    private final TailFace up;
    private final TailFace down;
    private final TailRotation rotation;

    public TailElement(String name, float[] from, float[] to, TailFace north, TailFace south,
                       TailFace east, TailFace west, TailFace up, TailFace down, TailRotation rotation) {
        this.name = name;
        this.from = from;
        this.to = to;
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
        this.up = up;
        this.down = down;
        this.rotation = rotation;
    }

    public String name() { return name; }
    public float[] from() { return from; }
    public float[] to() { return to; }
    public TailFace north() { return north; }
    public TailFace south() { return south; }
    public TailFace east() { return east; }
    public TailFace west() { return west; }
    public TailFace up() { return up; }
    public TailFace down() { return down; }
    public TailRotation rotation() { return rotation; }
}
