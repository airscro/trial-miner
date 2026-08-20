package com.example.trialminer.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric single-player/server implementation of TrialMiner's safe mining rule.
 *
 * <p>Vanilla does not expose a stable item format for partially completed trial
 * spawners. The mod therefore only serializes spawners after a trial has ended
 * (INACTIVE or COOLDOWN), so replacing the item can never reset a live mob
 * quota.</p>
 */
public final class TrialMinerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(this::beforeBlockBreak);
    }

    private boolean beforeBlockBreak(Level level, Player player, BlockPos pos,
            BlockState state, BlockEntity blockEntity) {
        if (level.isClientSide() || !state.is(Blocks.TRIAL_SPAWNER)) {
            return true;
        }

        TrialSpawnerState trialState = state.getValue(TrialSpawnerBlock.STATE);
        if (trialState != TrialSpawnerState.INACTIVE && trialState != TrialSpawnerState.COOLDOWN) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "You cannot mine a trial spawner while its trial is in progress."));
            }
            return false;
        }

        if (!(blockEntity instanceof TrialSpawnerBlockEntity spawner)
                || !hasSilkTouch(level, player.getMainHandItem())) {
            return true;
        }

        ItemStack drop = new ItemStack(Blocks.TRIAL_SPAWNER);
        drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(
                BlockEntityType.TRIAL_SPAWNER,
                spawner.saveWithoutMetadata(level.registryAccess())));

        // Returning false suppresses vanilla's normal no-drop trial-spawner
        // break. Remove the block and create exactly one state-preserving drop.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, drop));
        return false;
    }

    private boolean hasSilkTouch(Level level, ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SILK_TOUCH),
                tool) > 0;
    }
}
