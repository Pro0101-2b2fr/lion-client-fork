package com.lionclient.gui;

import java.util.Random;

/**
 * Snowflake particle used by the modern click GUI background effect.
 */
final class ModernClickGuiSnowflake {
    float x;
    float y;
    final int size;
    final float speed;
    final float swingAmount;
    final float swingSpeed;
    final int alpha;
    float age;

    ModernClickGuiSnowflake(float x, float y, int size, float speed,
                            float swingAmount, float swingSpeed, int alpha) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.speed = speed;
        this.swingAmount = swingAmount;
        this.swingSpeed = swingSpeed;
        this.alpha = alpha;
        this.age = 0.0F;
    }

    static ModernClickGuiSnowflake create(Random random, int screenWidth, int screenHeight, boolean randomY) {
        float startY = randomY
            ? random.nextFloat() * Math.max(1, screenHeight)
            : -8.0F - random.nextFloat() * 18.0F;
        return new ModernClickGuiSnowflake(
            random.nextFloat() * Math.max(1, screenWidth),
            startY,
            4 + random.nextInt(5),
            26.0F + random.nextFloat() * 38.0F,
            8.0F + random.nextFloat() * 16.0F,
            1.5F + random.nextFloat() * 2.5F,
            110 + random.nextInt(90)
        );
    }
}
