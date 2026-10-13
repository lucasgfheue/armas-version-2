package com.example.armasreales;

/** Versión dorada del fusil de asalto: misma cadencia automática, pero mucho más daño (4 corazones por bala). */
public class GoldenRifleItem extends AssaultRifleItem {
    private static final float GOLDEN_DAMAGE = 8.0f;

    public GoldenRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    protected float damage() {
        return GOLDEN_DAMAGE;
    }
}
