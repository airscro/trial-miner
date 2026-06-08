package com.example.trialminer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TrialSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.spawner.TrialSpawnerConfiguration;

public final class TrialSpawnerListener implements Listener {

    private static final long RESTORE_DELAY_TICKS = 2L;
    private static final int DEFAULT_COOLDOWN_TICKS = 36000;

    private final TrialMinerPlugin plugin;
    private final NamespacedKey idKey;

    private final Map<UUID, TrialSpawner> stateCache = new ConcurrentHashMap<>();

    public TrialSpawnerListener(TrialMinerPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "spawner_state_id");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.TRIAL_SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        if (!mayMine(player)) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof TrialSpawner spawnerState)) {
            return;
        }

        event.setCancelled(true);

        UUID id = UUID.randomUUID();
        stateCache.put(id, spawnerState);

        ItemStack drop = buildSpawnerItem(spawnerState, id);

        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        if (plugin.getConfig().getBoolean("break-effect", true)) {
            world.playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        }

        block.setType(Material.AIR, false);

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative || plugin.getConfig().getBoolean("drop-in-creative", true)) {
            world.dropItemNaturally(center, drop);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.TRIAL_SPAWNER) {
            return;
        }

        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String idStr = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (idStr == null) {
            return;
        }

        TrialSpawner source;
        try {
            source = stateCache.get(UUID.fromString(idStr));
        } catch (IllegalArgumentException ex) {
            source = null;
        }
        if (source == null) {
            return;
        }

        final TrialSpawner src = source;
        final Location loc = block.getLocation();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restoreState(loc, src), RESTORE_DELAY_TICKS);
    }

    private void restoreState(Location loc, TrialSpawner stored) {
        Block block = loc.getBlock();
        if (block.getType() != Material.TRIAL_SPAWNER) {
            return;
        }

        boolean preserveOminous = plugin.getConfig().getBoolean("preserve-ominous", true);
        boolean ominous = stored.isOminous();

        if (preserveOminous && ominous) {
            BlockData data = block.getBlockData();
            if (data instanceof org.bukkit.block.data.type.TrialSpawner spawnerData) {
                spawnerData.setOminous(true);
                block.setBlockData(spawnerData, false);
            }
        }

        if (!(block.getState() instanceof TrialSpawner live)) {
            return;
        }

        copyConfiguration(stored.getNormalConfiguration(), live.getNormalConfiguration());
        copyConfiguration(stored.getOminousConfiguration(), live.getOminousConfiguration());

        trySet(() -> live.setRequiredPlayerRange(stored.getRequiredPlayerRange()));

        int storedCooldown;
        try {
            storedCooldown = stored.getCooldownLength();
        } catch (Throwable t) {
            storedCooldown = 0;
        }
        final int cooldown = storedCooldown > 0 ? storedCooldown : DEFAULT_COOLDOWN_TICKS;
        final long cooldownEnd = loc.getWorld().getGameTime() + cooldown;
        trySet(() -> live.setCooldownLength(cooldown));
        trySet(() -> live.setCooldownEnd(cooldownEnd));
        trySet(() -> live.setNextSpawnAttempt(0));

        if (preserveOminous) {
            trySet(() -> live.setOminous(ominous));
        }

        live.update(true, false);
    }

    private void copyConfiguration(TrialSpawnerConfiguration from, TrialSpawnerConfiguration to) {
        if (from == null || to == null) {
            return;
        }
        trySet(() -> to.setBaseSpawnsBeforeCooldown(from.getBaseSpawnsBeforeCooldown()));
        trySet(() -> to.setAdditionalSpawnsBeforeCooldown(from.getAdditionalSpawnsBeforeCooldown()));
        trySet(() -> to.setBaseSimultaneousEntities(from.getBaseSimultaneousEntities()));
        trySet(() -> to.setAdditionalSimultaneousEntities(from.getAdditionalSimultaneousEntities()));
        trySet(() -> to.setSpawnRange(from.getSpawnRange()));
        trySet(() -> {
            var rewards = from.getPossibleRewards();
            if (rewards != null && !rewards.isEmpty()) {
                to.setPossibleRewards(rewards);
            }
        });
        trySet(() -> {
            var spawns = from.getPotentialSpawns();
            if (spawns != null && !spawns.isEmpty()) {
                to.setPotentialSpawns(spawns);
            }
        });
        trySet(() -> {
            var entity = from.getSpawnedEntity();
            if (entity != null) {
                to.setSpawnedEntity(entity);
            } else if (from.getSpawnedType() != null) {
                to.setSpawnedType(from.getSpawnedType());
            }
        });
    }

    private ItemStack buildSpawnerItem(BlockState state, UUID id) {
        ItemStack drop = new ItemStack(Material.TRIAL_SPAWNER);
        ItemMeta meta = drop.getItemMeta();
        if (meta == null) {
            return drop;
        }
        if (meta instanceof BlockStateMeta blockStateMeta) {
            blockStateMeta.setBlockState(state);
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
        drop.setItemMeta(meta);
        return drop;
    }

    private boolean mayMine(Player player) {
        if (plugin.getConfig().getBoolean("require-permission", true)
                && !player.hasPermission("trialminer.mine")) {
            return false;
        }
        if (plugin.getConfig().getBoolean("require-silk-touch", true)
                && !hasSilkTouch(player.getInventory().getItemInMainHand())) {
            return false;
        }
        return true;
    }

    private boolean hasSilkTouch(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return false;
        }
        for (Enchantment enchantment : tool.getEnchantments().keySet()) {
            if (enchantment.getKey().getKey().equals("silk_touch")) {
                return true;
            }
        }
        return false;
    }

    private void trySet(Runnable setter) {
        try {
            setter.run();
        } catch (Throwable ignored) {
        }
    }
}
