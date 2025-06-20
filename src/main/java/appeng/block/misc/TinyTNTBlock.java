/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.misc;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseBlock;
import appeng.entity.TinyTNTPrimedEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TinyTNTBlock extends AEBaseBlock {

    private static final VoxelShape SHAPE = Shapes
            .create(new AABB(0.25f, 0.0f, 0.25f, 0.75f, 0.5f, 0.75f));

    public TinyTNTBlock(Properties props) {
        super(props);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 2; // FIXME: Validate that this is the correct value range
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (heldItem.is(ItemTags.CREEPER_IGNITERS)) {
            onCaughtFire(state, level, pos, hit.getDirection(), player);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
            Item item = heldItem.getItem();
            if (!player.isCreative()) {
                if (heldItem.has(DataComponents.DAMAGE)) {
                    heldItem.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                } else {
                    heldItem.shrink(1);
                }
            }

            player.awardStat(Stats.ITEM_USED.get(item));
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction,
            @Nullable LivingEntity igniter) {
        this.startFuse(level, pos, igniter);
    }

    public void startFuse(Level level, BlockPos pos, LivingEntity igniter) {
        if (!level.isClientSide) {
            var primedTinyTNTEntity = new TinyTNTPrimedEntity(level, pos.getX() + 0.5F, pos.getY(),
                    pos.getZ() + 0.5F, igniter);
            level.addFreshEntity(primedTinyTNTEntity);
            level.playSound(null, primedTinyTNTEntity.getX(), primedTinyTNTEntity.getY(),
                    primedTinyTNTEntity.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1, 1);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block blockIn, BlockPos fromPos,
            boolean isMoving) {
        if (level.getBestNeighborSignal(pos) > 0) {
            this.startFuse(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (level.getBestNeighborSignal(pos) > 0) {
            this.startFuse(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof AbstractArrow arrow) {

            if (arrow.isOnFire()) {
                LivingEntity igniter = null;
                // Check if the shooter still exists
                Entity shooter = arrow.getOwner();
                if (shooter instanceof LivingEntity) {
                    igniter = (LivingEntity) shooter;
                }
                this.startFuse(level, pos, igniter);
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    public boolean dropFromExplosion(Explosion exp) {
        return false;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion exp) {
        super.wasExploded(level, pos, exp);
        if (!level.isClientSide) {
            final TinyTNTPrimedEntity primedTinyTNTEntity = new TinyTNTPrimedEntity(level, pos.getX() + 0.5F,
                    pos.getY(), pos.getZ() + 0.5F, exp.getIndirectSourceEntity());
            primedTinyTNTEntity
                    .setFuse(level.random.nextInt(primedTinyTNTEntity.getFuse() / 4)
                            + primedTinyTNTEntity.getFuse() / 8);
            level.addFreshEntity(primedTinyTNTEntity);
        }
    }

}
