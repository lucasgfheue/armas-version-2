package com.example.armasreales;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AmmoUtil {
    private AmmoUtil() {}

    /** Busca el primer stack del ítem indicado en el inventario (y mano secundaria). */
    public static ItemStack find(Player player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
