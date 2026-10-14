package com.example.armasreales.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Construye, columna a columna, las vías, la estación y las paradas. */
public final class RailBuilder {
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private RailBuilder() {
    }

    /** Construye todo lo que corresponda dentro de un chunk ya cargado. */
    public static void buildChunk(ServerLevel level, RailData data, int chunkX, int chunkZ) {
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = (chunkX << 4) + dx;
                int z = (chunkZ << 4) + dz;
                RailData.Plan plan = data.plan(x, z);
                if (plan != null) {
                    buildColumn(level, data, x, z, plan);
                }
            }
        }
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos) != state) {
            level.setBlock(pos, state, FLAGS);
        }
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    private static void buildColumn(ServerLevel level, RailData data, int x, int z, RailData.Plan plan) {
        int y = data.deckY;
        boolean inStation = data.inStation(x, z);
        int clear = inStation ? 4 : 3;
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1) despejar el espacio sobre la vía
        for (int dy = 1; dy <= clear; dy++) {
            set(level, x, y + dy, z, air);
        }

        // 2) lecho
        BlockState bed;
        switch (plan.kind) {
            case RAIL -> bed = plan.powered ? Blocks.REDSTONE_BLOCK.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState();
            case BUFFER -> bed = Blocks.STONE_BRICKS.defaultBlockState();
            case STATION -> {
                int adx = Math.abs(plan.a), adz = Math.abs(plan.b);
                bed = (adz == 7 || adx == 16) ? Blocks.STONE_BRICKS.defaultBlockState()
                        : Blocks.SMOOTH_STONE.defaultBlockState();
            }
            default -> bed = Blocks.SMOOTH_STONE.defaultBlockState();
        }
        set(level, x, y, z, bed);

        // 3) apoyo hacia abajo (terraplén o pilares de viaducto)
        int g = y - 1;
        int limit = Math.max(level.getMinBuildHeight() + 1, y - 90);
        while (g > limit && replaceable(level.getBlockState(new BlockPos(x, g, z)))) {
            g--;
        }
        int depth = (y - 1) - g;
        boolean fillAlways = inStation || plan.kind == RailData.Kind.HALT;
        boolean fill = fillAlways || depth <= 3 || Math.floorMod(x + z, 6) == 0;
        if (fill) {
            for (int yy = g + 1; yy < y; yy++) {
                set(level, x, yy, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }

        // 4) elementos sobre el lecho
        switch (plan.kind) {
            case RAIL -> {
                if (plan.powered) {
                    set(level, x, y + 1, z, Blocks.POWERED_RAIL.defaultBlockState()
                            .setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, plan.shape)
                            .setValue(BlockStateProperties.POWERED, true));
                } else {
                    set(level, x, y + 1, z, Blocks.RAIL.defaultBlockState()
                            .setValue(BlockStateProperties.RAIL_SHAPE, plan.shape));
                }
                // luz en los túneles
                if (!inStation && Math.floorMod(x + z, 8) == 0) {
                    BlockPos ceiling = new BlockPos(x, y + 4, z);
                    BlockState cs = level.getBlockState(ceiling);
                    if (!cs.isAir() && cs.canOcclude()) {
                        level.setBlock(ceiling, Blocks.GLOWSTONE.defaultBlockState(), FLAGS);
                    }
                }
            }
            case BUFFER -> {
                set(level, x, y + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                set(level, x, y + 2, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
            case STATION -> buildStationColumn(level, x, y, z, plan.a, plan.b);
            case HALT -> {
                if (Math.abs(plan.a) == 2 && (plan.b == 1 || plan.b == 7)) {
                    set(level, x, y + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                    set(level, x, y + 2, z, Blocks.STONE_BRICKS.defaultBlockState());
                    set(level, x, y + 3, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }

        // 5) techo de la estación (también sobre las vías)
        if (inStation) {
            set(level, x, y + 5, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }
    }

    private static void buildStationColumn(ServerLevel level, int x, int y, int z, int dx, int dz) {
        int adx = Math.abs(dx), adz = Math.abs(dz);

        boolean pillar = (adz == 7 && dx % 6 == 0) || (dz == 0 && adx != 0 && adx % 6 == 0);
        if (pillar) {
            for (int dy = 1; dy <= 4; dy++) {
                set(level, x, y + dy, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
            return;
        }

        // faroles colgando del techo
        if ((adz == 4 || dz == 0) && adx % 6 == 3 && adx <= 15) {
            set(level, x, y + 4, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // bancos
        if (adz == 5 && adx % 4 == 0 && adx >= 4 && adx <= 12) {
            Direction facing = dz < 0 ? Direction.SOUTH : Direction.NORTH;
            set(level, x, y + 1, z, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing));
        }

        // cofre de bienvenida
        if (dx == 1 && dz == 0) {
            BlockPos pos = new BlockPos(x, y + 1, z);
            level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), FLAGS);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.MINECART, 4));
                chest.setItem(1, new ItemStack(Items.BREAD, 16));
                chest.setItem(2, new ItemStack(Items.TORCH, 32));
                chest.setItem(3, new ItemStack(Items.RAIL, 16));
                chest.setChanged();
            }
        }
    }
}
