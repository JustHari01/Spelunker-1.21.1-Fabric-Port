package de.leximon.spelunker;

import de.leximon.spelunker.core.SpelunkerConfig;
import de.leximon.spelunker.core.SpelunkerEffectManager;
import de.leximon.spelunker.core.SpelunkerEffectRenderer;
import de.leximon.spelunker.mixin.client.WorldRendererAccessor;
import de.leximon.spelunker.network.SpelunkerPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.network.PacketByteBuf;

public class SpelunkerModClient implements ClientModInitializer {

    public static SpelunkerEffectRenderer spelunkerEffectRenderer = new SpelunkerEffectRenderer();

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SpelunkerConfig.initBlockHighlightConfig());

        // register renderer
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            WorldRendererAccessor worldRenderer = (WorldRendererAccessor) context.worldRenderer();
            if (spelunkerEffectRenderer.isActive())
                spelunkerEffectRenderer.render(context.matrixStack(), context.camera(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers());
        });

        ClientPlayNetworking.registerGlobalReceiver(SpelunkerPayloads.OreChunks.ID, (payload, context) -> {
            PacketByteBuf buffer = payload.openBuffer();
            try {
                SpelunkerEffectManager.readPacket(spelunkerEffectRenderer, buffer);
            } finally {
                buffer.release();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(SpelunkerPayloads.ConfigSnapshot.ID, (payload, context) -> {
            PacketByteBuf buffer = payload.openBuffer();
            try {
                SpelunkerConfig.readPacket(buffer);
            } finally {
                buffer.release();
            }
        });
    }
}
