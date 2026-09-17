package org.bukkit.block;
import org.bukkit.inventory.Inventory;
public class Hopper implements BlockState {
    public Inventory getInventory() { return new Inventory(); }
    public Block getBlock() { return new Block(); }
}
