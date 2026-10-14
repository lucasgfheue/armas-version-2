package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Ítem que coloca un tanque en el suelo. */
public class TankItem extends Item {
    public TankItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        TankEntity tank = new TankEntity(ModEntities.TANK.get(), level);
        float yaw = player != null ? player.getYRot() : 0.0f;
        tank.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);

        if (!level.noCollision(tank)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("No hay espacio para el tanque (3x3 libre)"), true);
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
