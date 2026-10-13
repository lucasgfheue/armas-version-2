package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // TNT: bloque de TNT (tamaño x2)
        event.registerEntityRenderer(ModEntities.TNT_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 2.0F, false));

        // Bengala: bola de fuego gigante con halo brillante
        event.registerEntityRenderer(ModEntities.FLARE_PROJECTILE.get(), FlareRenderer::new);

        // Rayos del láser y del plasma
        event.registerEntityRenderer(ModEntities.BEAM.get(), BeamRenderer::new);
    }

    /** Los brazos se levantan al frente, como al sostener una ballesta: se ve que sostiene el arma. */
    @SubscribeEvent
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions holdGun = new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                // Sólo levanta el arma mientras apuntas; si no, pose normal (brazo abajo)
                boolean aiming = entity.isUsingItem() && entity.getUseItem() == stack;
                return aiming ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }
        };
        event.registerItem(holdGun, ModItems.PISTOLA.get(), ModItems.RIFLE_LASER.get(),
                ModItems.LANZADOR_TNT.get(), ModItems.PISTOLA_BENGALAS.get(), ModItems.RIFLE_PLASMA.get());
    }

    /** Propiedad "aiming": el modelo del arma cambia mientras apuntas (rotación para brazo levantado). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ResourceLocation aiming = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "aiming");
            Item[] guns = {ModItems.PISTOLA.get(), ModItems.RIFLE_LASER.get(), ModItems.LANZADOR_TNT.get(),
                    ModItems.PISTOLA_BENGALAS.get(), ModItems.RIFLE_PLASMA.get()};
            for (Item gun : guns) {
                ItemProperties.register(gun, aiming, (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
        });
    }
}
