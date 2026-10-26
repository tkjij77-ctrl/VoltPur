package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class VoltPurSmartItemLimiterTest {

    private Server server;
    private World world;

    @BeforeEach
    void setUp() {
        VoltPurConfig.performanceEnabled = true;
        VoltPurConfig.autotuneSmartItemLimiter = true;
        VoltPurConfig.maxItemsPerWorld = 3;
        VoltPurConfig.autotuneItemExemptTicks = 100; // 5 seconds
        VoltPurConfig.autotuneItemExemptRadius = 8.0;

        server = mock(Server.class);
        world = mock(World.class);

        when(server.getLogger()).thenReturn(Logger.getLogger("VoltPurItemTest"));
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getName()).thenReturn("world");

        VoltPurTestHelper.setMockServer(server);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        VoltPurTestHelper.clearMockServer();
    }

    private Item createMockItem(int ticksLived, Location loc) {
        Item item = mock(Item.class);
        when(item.getTicksLived()).thenReturn(ticksLived);
        when(item.getLocation()).thenReturn(loc);
        when(item.isValid()).thenReturn(true);
        return item;
    }

    @Test
    void testOldestItemsPrunedFirst() {
        Location farLoc = new Location(world, 1000, 64, 1000);
        when(world.getPlayers()).thenReturn(new ArrayList<>());

        // Create 5 items (cap is 3, so 2 should be removed)
        Item item1 = createMockItem(200, farLoc);
        Item item2 = createMockItem(500, farLoc); // oldest
        Item item3 = createMockItem(450, farLoc); // second oldest
        Item item4 = createMockItem(150, farLoc);
        Item item5 = createMockItem(300, farLoc);

        List<Entity> entities = new ArrayList<>(List.of(item1, item2, item3, item4, item5));
        when(world.getEntities()).thenReturn(entities);

        int removed = VoltPurPerformance.performCleanup();
        assertEquals(2, removed);

        // item2 (500) and item3 (450) must have been removed!
        verify(item2).remove();
        verify(item3).remove();
        verify(item1, never()).remove();
        verify(item4, never()).remove();
        verify(item5, never()).remove();
    }

    @Test
    void testRecentItemsExemptFromCleanup() {
        Location farLoc = new Location(world, 1000, 64, 1000);
        when(world.getPlayers()).thenReturn(new ArrayList<>());

        // 4 items, but 3 of them are younger than 100 ticks
        Item young1 = createMockItem(40, farLoc);
        Item young2 = createMockItem(60, farLoc);
        Item young3 = createMockItem(90, farLoc);
        Item old1 = createMockItem(600, farLoc);

        List<Entity> entities = new ArrayList<>(List.of(young1, young2, young3, old1));
        when(world.getEntities()).thenReturn(entities);

        // Total items is 4, cap is 3, so toRemove = 1.
        // Old item is eligible; young items are exempt.
        int removed = VoltPurPerformance.performCleanup();
        assertEquals(1, removed);

        verify(old1).remove();
        verify(young1, never()).remove();
        verify(young2, never()).remove();
        verify(young3, never()).remove();
    }

    @Test
    void testNearPlayerItemsExemptFromCleanup() {
        Player player = mock(Player.class);
        Location playerLoc = new Location(world, 100, 64, 100);
        when(player.getLocation()).thenReturn(playerLoc);
        when(world.getPlayers()).thenReturn(List.of(player));

        // 4 items:
        // itemNear1: distance 2 blocks (near player -> exempt)
        Location nearLoc = new Location(world, 102, 64, 100);
        Item itemNear = createMockItem(800, nearLoc);

        // itemFar1, itemFar2, itemFar3: far from player
        Location farLoc = new Location(world, 500, 64, 500);
        Item itemFar1 = createMockItem(600, farLoc);
        Item itemFar2 = createMockItem(400, farLoc);
        Item itemFar3 = createMockItem(300, farLoc);

        List<Entity> entities = new ArrayList<>(List.of(itemNear, itemFar1, itemFar2, itemFar3));
        when(world.getEntities()).thenReturn(entities);

        // Cap is 3, total is 4 -> toRemove is 1.
        // itemNear has highest ticks (800), but is exempt due to player proximity!
        // So itemFar1 (600 ticks) should be removed instead.
        int removed = VoltPurPerformance.performCleanup();
        assertEquals(1, removed);

        verify(itemNear, never()).remove();
        verify(itemFar1).remove();
        verify(itemFar2, never()).remove();
        verify(itemFar3, never()).remove();
    }
}
