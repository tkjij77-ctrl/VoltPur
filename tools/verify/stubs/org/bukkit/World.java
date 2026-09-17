package org.bukkit;
import java.io.File;
import java.util.List;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.SpawnCategory;
public class World {
    public enum Environment { NORMAL, NETHER, THE_END }
    public String getName() { return "world"; }
    public Environment getEnvironment() { return Environment.NORMAL; }
    public List<Entity> getEntities() { return List.of(); }
    public Chunk[] getLoadedChunks() { return new Chunk[0]; }
    public File getWorldFolder() { return new File("world"); }
    public int getSpawnLimit(SpawnCategory category) { return -1; }
    public void setSpawnLimit(SpawnCategory category, int limit) { }
    public int getSimulationDistance() { return 10; }
    public void setSimulationDistance(int distance) { }
}
