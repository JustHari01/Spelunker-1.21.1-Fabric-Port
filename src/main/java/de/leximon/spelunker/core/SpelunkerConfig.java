package de.leximon.spelunker.core;

import de.leximon.spelunker.SpelunkerMod;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.hjson.JsonArray;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.hjson.Stringify;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class SpelunkerConfig {

    private static final Map<String[], ChunkBlockConfig> DEFAULT_BLOCK_CONFIGS = new HashMap<>();
    private static final String CONFIG_HEADER = """
            # Spelunker configuration (Hjson).
            # server-validating: scan on the server and send visible ore locations to clients; recommended with anti-xray.
            # allow-potion-brewing: controls brewing and amethyst-dust drops.
            # amethyst-dust-chance: percent chance per eligible amethyst-cluster drop.
            # short-potion-duration / long-potion-duration: effect durations in seconds.
            # loot-tables: structure loot tables and weights for the two potions.
            # block-transition: globally enables the outline transition animation.
            # block-configs: block IDs, outline colors, per-block transition and scan radius.
            # A block effect radius above 32 is not recommended.

            """;

    public static final ChunkBlockConfig NONE_BLOCK_CONFIG = new ChunkBlockConfig(0, false, 0);
    public static final List<LootTableEntry> LOOT_TABLES = new ArrayList<>();

    public record LootTableEntry(Identifier id, int min, int max, int shortChance, int longChance) {}

    public static boolean globalTransition = true;
    public static boolean serverValidating = true;
    public static boolean allowPotionBrewing = true;
    public static int chunkRadius = 1;
    public static int amethystChance = 10;
    public static int shortPotionDuration = 45;
    public static int longPotionDuration = 90;
    public static Object2ObjectMap<Block, ChunkBlockConfig> blockConfigs = new Object2ObjectOpenHashMap<>();

    private static Consumer<Void> blockConfigInitializer = v -> {};
    private static boolean blockHighlightInitialized = false;

    public static final java.io.File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("spelunker.hjson").toFile();

    public static void createDefaultConfig() throws IOException {
        JsonObject obj;
        boolean rewrite = !CONFIG_FILE.exists();
        if (CONFIG_FILE.exists()) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                obj = JsonValue.readHjson(reader).asObject();
            }
        } else {
            obj = new JsonObject();
        }

        if (!hasBoolean(obj, "server-validating")) {
            obj.set("server-validating", serverValidating);
            rewrite = true;
        }
        if (!hasBoolean(obj, "allow-potion-brewing")) {
            obj.set("allow-potion-brewing", allowPotionBrewing);
            rewrite = true;
        }
        if (!hasInt(obj, "amethyst-dust-chance")) {
            obj.set("amethyst-dust-chance", amethystChance);
            rewrite = true;
        }
        if (!hasInt(obj, "short-potion-duration")) {
            obj.set("short-potion-duration", shortPotionDuration);
            rewrite = true;
        }
        if (!hasInt(obj, "long-potion-duration")) {
            obj.set("long-potion-duration", longPotionDuration);
            rewrite = true;
        }
        if (!hasArray(obj, "loot-tables")) {
            int min = 1;
            int max = 1;
            int shortPotionChance = 10;
            int longPotionChance = 25;

            JsonValue legacyValue = obj.get("loot-table");
            if (legacyValue != null && legacyValue.isObject()) {
                JsonObject oldLoot = legacyValue.asObject();
                JsonValue rollsValue = oldLoot.get("rolls");
                if (rollsValue != null && rollsValue.isObject()) {
                    JsonObject rolls = rollsValue.asObject();
                    min = rolls.getInt("min", min);
                    max = rolls.getInt("max", max);
                }
                shortPotionChance = oldLoot.getInt("short-potion-chance", shortPotionChance);
                longPotionChance = oldLoot.getInt("long-potion-chance", longPotionChance);
                obj.remove("loot-table");
            }

            JsonObject entry = new JsonObject()
                    .add("targetId", "minecraft:chests/abandoned_mineshaft")
                    .add("min", min)
                    .add("max", max)
                    .add("short-potion-chance", shortPotionChance)
                    .add("long-potion-chance", longPotionChance);
            obj.set("loot-tables", new JsonArray().add(entry));
            rewrite = true;
        }
        if (!hasBoolean(obj, "block-transition")) {
            obj.set("block-transition", globalTransition);
            rewrite = true;
        }
        if (!hasArray(obj, "block-configs")) {
            JsonArray list = new JsonArray();
            JsonValue legacyColorsValue = obj.get("block-highlight-colors");
            if (legacyColorsValue != null && legacyColorsValue.isArray()) {
                int effectRadius = obj.getInt("effect-radius", 16);
                for (JsonValue value : legacyColorsValue.asArray()) {
                    if (!value.isObject()) continue;
                    JsonObject legacyEntry = value.asObject();
                    legacyEntry.set("transition", true);
                    legacyEntry.set("effectRadius", effectRadius);
                    list.add(legacyEntry);
                }
                obj.remove("block-highlight-colors");
                obj.remove("effect-radius");
            } else {
                for (Map.Entry<String[], ChunkBlockConfig> entry : DEFAULT_BLOCK_CONFIGS.entrySet()) {
                    JsonArray idList = new JsonArray();
                    for (String id : entry.getKey()) idList.add(id);

                    String color = TextColor.fromRgb(entry.getValue().getColor()).getName();
                    list.add(new JsonObject()
                            .add("blockIds", idList)
                            .add("highlightColor", color == null ? "#ffffff" : color)
                            .add("transition", entry.getValue().isTransition())
                            .add("effectRadius", entry.getValue().getEffectRadius()));
                }
            }
            obj.set("block-configs", list);
            rewrite = true;
        }

        if (rewrite) {
            Files.createDirectories(CONFIG_FILE.toPath().getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                writer.write(CONFIG_HEADER);
                obj.writeTo(writer, Stringify.HJSON);
                writer.newLine();
            }
        }
        DEFAULT_BLOCK_CONFIGS.clear();
    }

    public static void loadConfig() throws IOException {
        JsonObject obj;
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            obj = JsonValue.readHjson(reader).asObject();
        }

        serverValidating = obj.getBoolean("server-validating", serverValidating);
        allowPotionBrewing = obj.getBoolean("allow-potion-brewing", allowPotionBrewing);
        shortPotionDuration = Math.max(1, obj.getInt("short-potion-duration", shortPotionDuration));
        longPotionDuration = Math.max(1, obj.getInt("long-potion-duration", longPotionDuration));
        amethystChance = Math.clamp(obj.getInt("amethyst-dust-chance", amethystChance), 0, 100);

        blockConfigs.clear();
        blockConfigInitializer = v -> {};
        chunkRadius = 1;
        JsonValue blockConfigsValue = obj.get("block-configs");
        if (blockConfigsValue != null && blockConfigsValue.isArray()) {
            for (JsonValue value : blockConfigsValue.asArray()) {
                if (!value.isObject()) continue;
                JsonObject blockObj = value.asObject();
                JsonValue blockIdsValue = blockObj.get("blockIds");
                if (blockIdsValue == null || !blockIdsValue.isArray()) continue;

                List<String> blockIds = new ArrayList<>();
                for (JsonValue blockIdValue : blockIdsValue.asArray()) {
                    if (blockIdValue.isString()) blockIds.add(blockIdValue.asString());
                }

                String hcolor = blockObj.getString("highlightColor", "#ffffff");
                TextColor textColor = TextColor.parse(hcolor).result().orElse(null);
                int color = 0xffffff;
                if (textColor == null) {
                    SpelunkerMod.LOGGER.error("Invalid color '{}' specified for the block(s) '{}'.", hcolor, StringUtils.join(blockIds, ", "));
                } else {
                    color = textColor.getRgb();
                }
                boolean transition = blockObj.getBoolean("transition", true);
                int effectRadius = blockObj.getInt("effectRadius", 1);
                if (effectRadius < 1) {
                    SpelunkerMod.LOGGER.warn("Effect radius '{}' for the block(s) '{}' is smaller than 1.", effectRadius, StringUtils.join(blockIds, ", "));
                    SpelunkerMod.LOGGER.warn("Setting it to 1.");
                    effectRadius = 1;
                }

                ChunkBlockConfig config = new ChunkBlockConfig(color, transition, effectRadius);
                blockConfigInitializer = blockConfigInitializer.andThen(v -> {
                    for (String blockId : blockIds) {
                        Identifier id = Identifier.tryParse(blockId);
                        if (id == null) {
                            SpelunkerMod.LOGGER.error("Invalid block id in config: '{}'.", blockId);
                            continue;
                        }
                        Optional<Block> optBlock = Registries.BLOCK.getOrEmpty(id);
                        if (optBlock.isEmpty()) {
                            SpelunkerMod.LOGGER.error("Unknown block id in config: '{}'.", blockId);
                        } else {
                            Block block = optBlock.get();
                            blockConfigs.put(block, config.setBlock(block));
                        }
                    }
                });
            }
        }

        LOOT_TABLES.clear();
        JsonValue lootTablesValue = obj.get("loot-tables");
        if (lootTablesValue != null && lootTablesValue.isArray()) {
            for (JsonValue value : lootTablesValue.asArray()) {
                if (!value.isObject()) continue;
                JsonObject entry = value.asObject();
                String targetId = entry.getString("targetId", "");
                Identifier id = Identifier.tryParse(targetId);
                if (id == null) {
                    SpelunkerMod.LOGGER.error("Invalid targetId in loot-tables: '{}'.", targetId);
                    continue;
                }
                LOOT_TABLES.add(new LootTableEntry(
                        id,
                        Math.max(0, entry.getInt("min", 1)),
                        Math.max(0, entry.getInt("max", 1)),
                        Math.clamp(entry.getInt("short-potion-chance", 10), 0, 100),
                        Math.clamp(entry.getInt("long-potion-chance", 25), 0, 100)
                ));
            }
        }
    }

    public static void writePacket(PacketByteBuf buf) {
        buf.writeBoolean(serverValidating);
        buf.writeVarInt(blockConfigs.size());
        for (Object2ObjectMap.Entry<Block, ChunkBlockConfig> entry : blockConfigs.object2ObjectEntrySet()) {
            ChunkBlockConfig conf = entry.getValue();
            buf.writeVarInt(Registries.BLOCK.getRawId(entry.getKey()));
            conf.write(buf);
        }
    }

    public static void readPacket(PacketByteBuf buf) {
        serverValidating = buf.readBoolean();
        blockConfigs.clear();
        int c = buf.readVarInt();
        for (int i = 0; i < c; i++)
            blockConfigs.put(Registries.BLOCK.get(buf.readVarInt()), new ChunkBlockConfig(buf));
    }

    public static void initBlockHighlightConfig() {
        if (blockHighlightInitialized) return;
        blockHighlightInitialized = true;
        blockConfigInitializer.accept(null);
    }

    public static boolean isOreBlock(Block block) {
        return blockConfigs.containsKey(block);
    }

    private static boolean hasArray(JsonObject object, String key) {
        JsonValue value = object.get(key);
        return value != null && value.isArray();
    }

    private static boolean hasBoolean(JsonObject object, String key) {
        JsonValue value = object.get(key);
        return value != null && value.isBoolean();
    }

    private static boolean hasInt(JsonObject object, String key) {
        JsonValue value = object.get(key);
        return value != null && value.isNumber();
    }

    static {
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:coal_ore", "minecraft:deepslate_coal_ore"}, new ChunkBlockConfig(0x505050, true, 16));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:iron_ore", "minecraft:deepslate_iron_ore"}, new ChunkBlockConfig(0xffd1bd, true, 8));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:copper_ore", "minecraft:deepslate_copper_ore"}, new ChunkBlockConfig(0xeb5e34, true, 12));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore"}, new ChunkBlockConfig(0xfff52e, true, 8));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"}, new ChunkBlockConfig(0x2ee0ff, true, 5));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"}, new ChunkBlockConfig(0x2eff35, true, 7));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"}, new ChunkBlockConfig(0x312eff, true, 8));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"}, new ChunkBlockConfig(0xff2e2e, true, 8));
        DEFAULT_BLOCK_CONFIGS.put(new String[] {"minecraft:nether_quartz_ore"}, new ChunkBlockConfig(0xffffff, true, 14));
    }

    public static class ChunkBlockConfig {

        private Block block;

        private final int color;
        private final boolean transition;
        private final int effectRadius;

        private int blockRadiusMax;
        private int blockRadiusMin;

        public ChunkBlockConfig(int color, boolean transition, int effectRadius) {
            this.color = color;
            this.transition = transition;
            this.effectRadius = effectRadius;
            parseEffectRadius();
        }

        public ChunkBlockConfig(PacketByteBuf buf) {
            this(buf.readInt(), buf.readBoolean(), buf.readVarInt());
        }

        public void write(PacketByteBuf buf) {
            buf.writeInt(color);
            buf.writeBoolean(transition);
            buf.writeVarInt(effectRadius);
        }

        private void parseEffectRadius() {
            int radiusInSections = (int) Math.ceil(effectRadius / 16f);
            if (radiusInSections > SpelunkerConfig.chunkRadius)
                SpelunkerConfig.chunkRadius = radiusInSections;
            blockRadiusMax = (int) Math.pow(effectRadius, 2);
            blockRadiusMin = (int) Math.pow(effectRadius - 1, 2);
        }

        public ChunkBlockConfig setBlock(Block block) {
            this.block = block;
            return this;
        }

        public Block getBlock() { return block; }
        public int getColor() { return color; }
        public boolean isTransition() { return transition; }
        public int getEffectRadius() { return effectRadius; }
        public int getBlockRadiusMax() { return blockRadiusMax; }
        public int getBlockRadiusMin() { return blockRadiusMin; }
    }
}
