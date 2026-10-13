package com.example.armasreales;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ArmasRealesMod.MOD_ID)
public class ArmasRealesMod {
    public static final String MOD_ID = "armasreales";

    public ArmasRealesMod(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(this::addToCreativeTabs);
        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FirePayload.TYPE, FirePayload.STREAM_CODEC, FirePayload::handle);
    }

    private void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.PISTOLA);
            event.accept(ModItems.MUNICION_PISTOLA);
            event.accept(ModItems.RIFLE_LASER);
            event.accept(ModItems.CELDA_ENERGIA);
            event.accept(ModItems.LANZADOR_TNT);
            event.accept(ModItems.PISTOLA_BENGALAS);
            event.accept(ModItems.RIFLE_PLASMA);
            event.accept(ModItems.TASER);
            event.accept(ModItems.PISTOLA_ACIDA);
            event.accept(ModItems.RIFLE_VIOLETA);
        }
    }
}
