package com.example.armasreales.rail;

import com.example.armasreales.ArmasRealesMod;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Estación de tren en el spawn y vías que se construyen solas, chunk a chunk, a medida que
 * se cargan: por eso la red no tiene límite de distancia.
 */
@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID)
public class RailEvents {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ConcurrentLinkedQueue<Long> PENDING = new ConcurrentLinkedQueue<>();
    private static final Map<Long, Integer> RETRIES = new HashMap<>();
    private static final int CHUNKS_PER_TICK = 4;
    private static final int CHECKS_PER_TICK = 400;
    private static final int FALLBACK_LENGTH = 2500;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            PENDING.add(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        RailData data = RailData.get(level);
        if (!data.initialized) {
            PENDING.clear();
            return;
        }
        int built = 0;
        int checks = CHECKS_PER_TICK;
        Long next;
        while (checks-- > 0 && built < CHUNKS_PER_TICK && (next = PENDING.poll()) != null) {
            long key = next;
            if (data.done.contains(key)) {
                continue;
            }
            int cx = net.minecraft.world.level.ChunkPos.getX(key);
            int cz = net.minecraft.world.level.ChunkPos.getZ(key);
            if (!data.chunkRelevant(cx, cz)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                int tries = RETRIES.merge(key, 1, Integer::sum);
                if (tries <= 5) {
                    PENDING.add(key);
                } else {
                    RETRIES.remove(key);
                }
                continue;
            }
            RETRIES.remove(key);
            RailBuilder.buildChunk(level, data, cx, cz);
            data.done.add(key);
            data.setDirty();
            built++;
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        RailData data = RailData.get(level);
        if (data.initialized) {
            return;
        }
        try {
            createNetwork(level, data, player);
        } catch (Exception e) {
            LOGGER.error("No se pudo crear la estación de tren", e);
        }
    }

    // ------------------------------------------------------------------ creación

    private static void createNetwork(ServerLevel level, RailData data, ServerPlayer player) {
        BlockPos spawn = level.getSharedSpawnPos();
        int sx = spawn.getX();
        int sz = spawn.getZ();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sx, sz);

        data.sx = sx;
        data.sz = sz;
        data.deckY = Mth.clamp(surface, 66, 100);

        int trackN = sz - 2;
        int trackS = sz + 2;

        // aldeas: dos por lado (este / oeste)
        for (int side : new int[]{1, -1}) {
            List<BlockPos> villages = findVillages(level, sx, sz, side);
            BlockPos v1 = villages.size() > 0 ? villages.get(0) : null; // la más cercana
            BlockPos v2 = villages.size() > 1 ? villages.get(1) : null;

            BlockPos forN = null;
            BlockPos forS = null;
            if (v1 != null && v2 == null) {
                if (v1.getZ() < sz) forN = v1; else forS = v1;
            } else if (v1 != null) {
                boolean n1 = v1.getZ() < sz;
                boolean n2 = v2.getZ() < sz;
                if (n1 != n2) {
                    forN = n1 ? v1 : v2;
                    forS = n1 ? v2 : v1;
                } else if (n1) {      // ambas al norte: la cercana por la vía norte
                    forN = v1;
                    forS = v2;
                } else {              // ambas al sur: la cercana por la vía sur
                    forS = v1;
                    forN = v2;
                }
            }
            data.routes.add(routeFor(sx, trackN, side, forN));
            data.routes.add(routeFor(sx, trackS, side, forS));
        }

        data.initialized = true;
        data.setDirty();

        // construir lo que ya está cargado alrededor de la estación y de las primeras vías
        int pcx = sx >> 4;
        int pcz = sz >> 4;
        for (int cx = pcx - 20; cx <= pcx + 20; cx++) {
            for (int cz = pcz - 20; cz <= pcz + 20; cz++) {
                if (!data.chunkRelevant(cx, cz)) {
                    continue;
                }
                if (level.getChunkSource().getChunkNow(cx, cz) != null) {
                    RailBuilder.buildChunk(level, data, cx, cz);
                    data.done.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                }
            }
        }
        data.setDirty();

        // vagonetas listas en cada vía
        int y = data.deckY + 1;
        for (int z : new int[]{trackN, trackS}) {
            level.addFreshEntity(new Minecart(level, sx + 4.5, y + 0.0625, z + 0.5));
            level.addFreshEntity(new Minecart(level, sx - 4.5, y + 0.0625, z + 0.5));
        }

        // spawn en la estación
        BlockPos station = new BlockPos(sx, data.deckY + 1, sz);
        level.setDefaultSpawnPos(station, 0.0f);
        player.teleportTo(level, sx + 0.5, data.deckY + 1, sz + 0.5, player.getYRot(), player.getXRot());
        player.setRespawnPosition(Level.OVERWORLD, station, 0.0f, true, false);
        player.sendSystemMessage(Component.literal(
                "Bienvenido a la estación. Subite a una vagoneta: las vías llevan a las aldeas (usá /estacion para volver)."));
    }

    private static RailData.Route routeFor(int sx, int trackZ, int side, BlockPos village) {
        if (village == null) {
            return new RailData.Route(trackZ, side, sx + side * FALLBACK_LENGTH, trackZ);
        }
        int ez = village.getZ();
        if (Math.abs(ez - trackZ) < 3) {
            ez = trackZ;
        }
        return new RailData.Route(trackZ, side, village.getX(), ez);
    }

    /** Hasta dos aldeas del lado indicado, la más cercana primero. */
    private static List<BlockPos> findVillages(ServerLevel level, int sx, int sz, int side) {
        List<BlockPos> found = new ArrayList<>();
        int[] offsets = {-500, 500};
        for (int off : offsets) {
            BlockPos center = new BlockPos(sx + side * 600, 64, sz + off);
            BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, center, 40, false);
            if (village == null) {
                continue;
            }
            if ((village.getX() - sx) * side < 60) {
                continue;
            }
            boolean duplicate = false;
            for (BlockPos p : found) {
                if (Math.abs(p.getX() - village.getX()) < 32 && Math.abs(p.getZ() - village.getZ()) < 32) {
                    duplicate = true;
                }
            }
            if (!duplicate) {
                found.add(village);
            }
        }
        found.sort((a, b) -> Integer.compare(Math.abs(a.getX() - sx), Math.abs(b.getX() - sx)));
        return found;
    }

    // ------------------------------------------------------------------ comando

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("estacion").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = ctx.getSource().getServer().overworld();
            RailData data = RailData.get(level);
            if (!data.initialized) {
                ctx.getSource().sendFailure(Component.literal("La estación todavía no fue creada."));
                return 0;
            }
            player.teleportTo(level, data.sx + 0.5, data.deckY + 1, data.sz + 0.5, player.getYRot(), 0.0f);
            return Command.SINGLE_SUCCESS;
        }));
    }
}
