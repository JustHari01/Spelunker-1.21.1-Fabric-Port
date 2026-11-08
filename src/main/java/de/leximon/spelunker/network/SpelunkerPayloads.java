package de.leximon.spelunker.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class SpelunkerPayloads {

    private SpelunkerPayloads() {}

    public record OreChunks(byte[] bytes) implements CustomPayload {
        public static final Id<OreChunks> ID = new Id<>(Identifier.of("spelunker", "ore_chunks"));
        public static final PacketCodec<RegistryByteBuf, OreChunks> CODEC = PacketCodec.tuple(
                PacketCodecs.BYTE_ARRAY,
                OreChunks::bytes,
                OreChunks::new
        );

        public OreChunks {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static OreChunks fromBuffer(PacketByteBuf buffer) {
            return new OreChunks(copyAndRelease(buffer));
        }

        public PacketByteBuf openBuffer() {
            return new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        }
    }

    public record ConfigSnapshot(byte[] bytes) implements CustomPayload {
        public static final Id<ConfigSnapshot> ID = new Id<>(Identifier.of("spelunker", "config"));
        public static final PacketCodec<RegistryByteBuf, ConfigSnapshot> CODEC = PacketCodec.tuple(
                PacketCodecs.BYTE_ARRAY,
                ConfigSnapshot::bytes,
                ConfigSnapshot::new
        );

        public ConfigSnapshot {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static ConfigSnapshot fromBuffer(PacketByteBuf buffer) {
            return new ConfigSnapshot(copyAndRelease(buffer));
        }

        public PacketByteBuf openBuffer() {
            return new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        }
    }

    private static byte[] copyAndRelease(PacketByteBuf buffer) {
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }
}
