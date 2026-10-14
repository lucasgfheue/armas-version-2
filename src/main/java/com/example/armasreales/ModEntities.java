package com.example.armasreales;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ArmasRealesMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TntProjectile>> TNT_PROJECTILE =
            ENTITIES.register("proyectil_tnt", () ->
                    EntityType.Builder.<TntProjectile>of(TntProjectile::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("proyectil_tnt"));

    public static final DeferredHolder<EntityType<?>, EntityType<FlareProjectile>> FLARE_PROJECTILE =
            ENTITIES.register("proyectil_bengala", () ->
                    EntityType.Builder.<FlareProjectile>of(FlareProjectile::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("proyectil_bengala"));

    public static final DeferredHolder<EntityType<?>, EntityType<TaserProjectile>> TASER_PROJECTILE =
            ENTITIES.register("proyectil_taser", () ->
                    EntityType.Builder.<TaserProjectile>of(TaserProjectile::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("proyectil_taser"));

    public static final DeferredHolder<EntityType<?>, EntityType<AcidProjectile>> ACID_PROJECTILE =
            ENTITIES.register("proyectil_acido", () ->
                    EntityType.Builder.<AcidProjectile>of(AcidProjectile::new, MobCategory.MISC)
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("proyectil_acido"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownKnife>> KNIFE =
            ENTITIES.register("cuchillo_arrojadizo", () ->
                    EntityType.Builder.<ThrownKnife>of(ThrownKnife::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("cuchillo_arrojadizo"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.example.armasreales.train.TrainCar>> TRAIN_CAR =
            ENTITIES.register("tren_vagon", () ->
                    EntityType.Builder.<com.example.armasreales.train.TrainCar>of(
                                    com.example.armasreales.train.TrainCar::new, MobCategory.MISC)
                            .sized(3.0f, 3.2f)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .noSave()
                            .fireImmune()
                            .build("tren_vagon"));

    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK =
            ENTITIES.register("tanque", () ->
                    EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                            .sized(2.5f, 1.2f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("tanque"));

    public static final DeferredHolder<EntityType<?>, EntityType<TankShell>> TANK_SHELL =
            ENTITIES.register("obus_tanque", () ->
                    EntityType.Builder.<TankShell>of(TankShell::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("obus_tanque"));

    public static final DeferredHolder<EntityType<?>, EntityType<BeamEntity>> BEAM =
            ENTITIES.register("rayo", () ->
                    EntityType.Builder.<BeamEntity>of(BeamEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("rayo"));
}
