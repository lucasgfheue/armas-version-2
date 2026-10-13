package com.example.armasreales;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ArmasRealesMod.MOD_ID);

    public static final DeferredItem<PistolItem> PISTOLA = ITEMS.registerItem(
            "pistola",
            PistolItem::new,
            new Item.Properties().stacksTo(1).durability(500));

    public static final DeferredItem<Item> MUNICION_PISTOLA = ITEMS.registerItem(
            "municion_pistola",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<LaserRifleItem> RIFLE_LASER = ITEMS.registerItem(
            "rifle_laser",
            LaserRifleItem::new,
            new Item.Properties().stacksTo(1).durability(400));

    public static final DeferredItem<Item> CELDA_ENERGIA = ITEMS.registerItem(
            "celda_energia",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<TntLauncherItem> LANZADOR_TNT = ITEMS.registerItem(
            "lanzador_tnt",
            TntLauncherItem::new,
            new Item.Properties().stacksTo(1).durability(150));

    public static final DeferredItem<FlareGunItem> PISTOLA_BENGALAS = ITEMS.registerItem(
            "pistola_bengalas",
            FlareGunItem::new,
            new Item.Properties().stacksTo(1).durability(120));

    public static final DeferredItem<PlasmaRifleItem> RIFLE_PLASMA = ITEMS.registerItem(
            "rifle_plasma",
            PlasmaRifleItem::new,
            new Item.Properties().stacksTo(1).durability(300));

    public static final DeferredItem<TaserItem> TASER = ITEMS.registerItem(
            "taser",
            TaserItem::new,
            new Item.Properties().stacksTo(1).durability(200));

    public static final DeferredItem<AcidPistolItem> PISTOLA_ACIDA = ITEMS.registerItem(
            "pistola_acida",
            AcidPistolItem::new,
            new Item.Properties().stacksTo(1).durability(200));

    public static final DeferredItem<VioletRifleItem> RIFLE_VIOLETA = ITEMS.registerItem(
            "rifle_violeta",
            VioletRifleItem::new,
            new Item.Properties().stacksTo(1).durability(250));
}
