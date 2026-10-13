package com.example.armasreales;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/** Cuchillo: clic izquierdo = tajo cuerpo a cuerpo (rápido), clic derecho = lo arroja. */
public class KnifeItem extends Item {
    private static final int THROW_COOLDOWN_TICKS = 12;
    private static final float THROW_SPEED = 2.8f;

    public KnifeItem(Properties properties) {
        super(properties);
    }

    /** 4 de daño cuerpo a cuerpo (1 base + 3) y ataque rápido. */
    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            ThrownKnife knife = new ThrownKnife(level, player, stack.copyWithCount(1));
            knife.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, THROW_SPEED, 0.5f);
            if (player.getAbilities().instabuild) {
                knife.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            level.addFreshEntity(knife);
            level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                    SoundSource.PLAYERS, 1.0f, 1.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.6f, 1.5f);
        }

        player.getCooldowns().addCooldown(this, THROW_COOLDOWN_TICKS);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
