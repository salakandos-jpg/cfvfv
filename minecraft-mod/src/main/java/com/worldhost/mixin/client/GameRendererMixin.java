package com.worldhost.mixin.client;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    // Reserved for future HUD overlays (e.g. "World Open" indicator)
}
