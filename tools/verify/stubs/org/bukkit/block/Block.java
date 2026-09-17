package org.bukkit.block;
public class Block {
    public Block getRelative(BlockFace face) { return this; }
    public boolean isBlockPowered() { return false; }
    public boolean isBlockIndirectlyPowered() { return false; }
    public BlockState getState() { return null; }
}
