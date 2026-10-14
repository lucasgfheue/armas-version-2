package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Coloca un tanque sobre el bloque clicado. */
public class TankItem extends Item {
    public TankItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TankEntity tank = ModEntities.TANK.get().create(level);
        if (tank == null) {
            return InteractionResult.FAIL;
        }
        float yaw = player != null ? player.getYRot() : 0.0f;
        tank.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        if (!level.noCollision(tank)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("No hay espacio para el tanque"), true);
            }
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(tank);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
