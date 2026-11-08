package de.leximon.spelunker;

import de.leximon.spelunker.core.SpelunkerConfig;
import de.leximon.spelunker.network.SpelunkerPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.MatchToolLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetPotionLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.item.EnchantmentsPredicate;
import net.minecraft.predicate.item.EnchantmentPredicate;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.predicate.item.ItemSubPredicateTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class SpelunkerMod implements ModInitializer {

	public static final String MODID = "spelunker";
	public static final Logger LOGGER = LoggerFactory.getLogger("Spelunker");
	public static final Identifier AMETHYST_CLUSTER_LOOT_TABLE = Identifier.of("blocks/amethyst_cluster");

	public static Item AMETHYST_DUST;

	public static RegistryEntry<StatusEffect> STATUS_EFFECT_SPELUNKER;
	public static RegistryEntry<Potion> SPELUNKER_POTION;
	public static RegistryEntry<Potion> LONG_SPELUNKER_POTION;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(SpelunkerPayloads.OreChunks.ID, SpelunkerPayloads.OreChunks.CODEC);
		PayloadTypeRegistry.playS2C().register(SpelunkerPayloads.ConfigSnapshot.ID, SpelunkerPayloads.ConfigSnapshot.CODEC);

		// load config
		try {
			SpelunkerConfig.createDefaultConfig();
			SpelunkerConfig.loadConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SpelunkerConfig.initBlockHighlightConfig());

		// register
		AMETHYST_DUST = Registry.register(Registries.ITEM, identifier("amethyst_dust"), new Item(new Item.Settings()));

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(content -> content.add(AMETHYST_DUST));

		StatusEffect spelunkerEffect = Registry.register(Registries.STATUS_EFFECT, identifier("spelunker"), new SpelunkerStatusEffect());
		STATUS_EFFECT_SPELUNKER = Registries.STATUS_EFFECT.getEntry(spelunkerEffect);
		Potion spelunkerPotion = Registry.register(Registries.POTION, identifier("spelunker"), new Potion(new StatusEffectInstance(STATUS_EFFECT_SPELUNKER, 20 * SpelunkerConfig.shortPotionDuration)));
		Potion longSpelunkerPotion = Registry.register(Registries.POTION, identifier("long_spelunker"), new Potion(new StatusEffectInstance(STATUS_EFFECT_SPELUNKER, 20 * SpelunkerConfig.longPotionDuration)));
		SPELUNKER_POTION = Registries.POTION.getEntry(spelunkerPotion);
		LONG_SPELUNKER_POTION = Registries.POTION.getEntry(longSpelunkerPotion);

		// register recipes
		if(SpelunkerConfig.allowPotionBrewing) {
			FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
				builder.registerPotionRecipe(Potions.NIGHT_VISION, AMETHYST_DUST, SPELUNKER_POTION);
				builder.registerPotionRecipe(Potions.LONG_NIGHT_VISION, AMETHYST_DUST, LONG_SPELUNKER_POTION);
				builder.registerPotionRecipe(SPELUNKER_POTION, Items.REDSTONE, LONG_SPELUNKER_POTION);
			});
		}

		// add potion to loot tables
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			Identifier id = key.getValue();
			for (SpelunkerConfig.LootTableEntry entry : SpelunkerConfig.LOOT_TABLES) {
				if(entry.id().equals(id)) {
					LootNumberProvider rollProvider;
					if(entry.min() == entry.max())
						rollProvider = ConstantLootNumberProvider.create(entry.min());
					else rollProvider = UniformLootNumberProvider.create(entry.min(), entry.max());
					table.pool(LootPool.builder()
							.rolls(rollProvider)
							.with(ItemEntry.builder(Items.POTION)
								.apply(() -> SetPotionLootFunction.builder(SPELUNKER_POTION).build())
									.weight(entry.shortChance())
							)
							.with(ItemEntry.builder(Items.POTION)
								.apply(() -> SetPotionLootFunction.builder(LONG_SPELUNKER_POTION).build())
									.weight(entry.longChance())
							)
							.with(ItemEntry.builder(Items.AIR)
									.weight(100 - (entry.shortChance() + entry.longChance()))
							)
					);
				}
			}
			if(AMETHYST_CLUSTER_LOOT_TABLE.equals(id) && SpelunkerConfig.allowPotionBrewing) {
				var silkTouch = registries.getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
				table.pool(LootPool.builder()
						.rolls(ConstantLootNumberProvider.create(1))
								.conditionally(MatchToolLootCondition.builder(ItemPredicate.Builder.create()
												.subPredicate(ItemSubPredicateTypes.ENCHANTMENTS, EnchantmentsPredicate.enchantments(List.of(new EnchantmentPredicate(silkTouch, NumberRange.IntRange.ANY)))))
										.invert()
										.build()
								)
						.with(ItemEntry.builder(AMETHYST_DUST)
								.weight(SpelunkerConfig.amethystChance)
						)
						.with(ItemEntry.builder(Items.AIR)
								.weight(100 - SpelunkerConfig.amethystChance)
						)
				);
			}
		});
	}

	public static Identifier identifier(String name) {
		return Identifier.of(MODID, name);
	}

	private static class SpelunkerStatusEffect extends StatusEffect {
		public SpelunkerStatusEffect() {
			super(StatusEffectCategory.BENEFICIAL, 0xffe32e);
		}
	}
}
