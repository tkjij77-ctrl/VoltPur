package org.bukkit.inventory;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
    public boolean hasItemMeta() { return false; }
    public ItemMeta getItemMeta() { return new ItemMeta(); }
}
