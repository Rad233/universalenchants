package fuzs.universalenchants.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import fuzs.universalenchants.UniversalEnchants;
import fuzs.universalenchants.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ItemEnchantmentArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Collection;
import java.util.Map;

public class ModEnchantCommand {
	private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType(
		object -> Component.translatable("commands.enchant.failed.entity", object)
	);
	private static final DynamicCommandExceptionType ERROR_NO_ITEM = new DynamicCommandExceptionType(
		object -> Component.translatable("commands.enchant.failed.itemless", object)
	);
	private static final DynamicCommandExceptionType ERROR_INCOMPATIBLE = new DynamicCommandExceptionType(
		object -> Component.translatable("commands.enchant.failed.incompatible", object)
	);
	private static final Dynamic2CommandExceptionType ERROR_LEVEL_TOO_HIGH = new Dynamic2CommandExceptionType(
			(object, object2) -> Component.translatable("commands.enchant.failed.level", object, object2)
	);
	private static final SimpleCommandExceptionType ERROR_NOTHING_HAPPENED = new SimpleCommandExceptionType(Component.translatable("commands.enchant.failed"));

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
		commandDispatcher.register(
			Commands.literal("enchant")
				.requires(commandSourceStack -> commandSourceStack.hasPermission(2))
				.then(
					Commands.argument("targets", EntityArgument.entities())
						.then(
							Commands.argument("enchantment", ItemEnchantmentArgument.enchantment())
								.executes(
									commandContext -> enchant(
											commandContext.getSource(),
											EntityArgument.getEntities(commandContext, "targets"),
											ItemEnchantmentArgument.getEnchantment(commandContext, "enchantment"),
											1
										)
								)
								.then(
										// restrict this to 255, enchantment levels above 255 are not supported in vanilla and will be reset to that anyway
										// min of 0 wouldn't do anything in vanilla, but now we use it to remove enchantments
									Commands.argument("level", IntegerArgumentType.integer(0, 255))
										.executes(
											commandContext -> enchant(
													commandContext.getSource(),
													EntityArgument.getEntities(commandContext, "targets"),
													ItemEnchantmentArgument.getEnchantment(commandContext, "enchantment"),
													IntegerArgumentType.getInteger(commandContext, "level")
												)
										)
								)
						)
				)
		);
	}

	private static int enchant(CommandSourceStack commandSourceStack, Collection<? extends Entity> collection, Enchantment enchantment, int level) throws CommandSyntaxException {
		// removed max level check (/effect command doesn't have it as well)
		if (!UniversalEnchants.CONFIG.get(ServerConfig.class).fixEnchantCommand && level > enchantment.getMaxLevel()) {
			throw ERROR_LEVEL_TOO_HIGH.create(level, enchantment.getMaxLevel());
		}
		int successes = 0;
		for (Entity entity : collection) {
			if (entity instanceof LivingEntity livingEntity) {
				ItemStack itemStack = livingEntity.getMainHandItem();
				if (!itemStack.isEmpty()) {
					if (UniversalEnchants.CONFIG.get(ServerConfig.class).fixEnchantCommand) {
						ItemStack stack = itemStack;
						// handle books, don't forget to set the new stack to the main hand when successful
						if (stack.getCount() == 1 && stack.is(Items.BOOK)) {
							stack = new ItemStack(Items.ENCHANTED_BOOK);
							CompoundTag compoundTag = itemStack.getTag();
							if (compoundTag != null) {
								stack.setTag(compoundTag.copy());
							}
						}
						// allow overriding existing enchantment level
						if ((stack.is(Items.ENCHANTED_BOOK) || enchantment.canEnchant(stack)) && EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantments(stack).keySet().stream().filter(e -> e != enchantment).toList(), enchantment)) {
							Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
							// when attempting to override existing enchantment level, vanilla will just add it as a duplicate
							// this ensures the old entry is overridden instead, this method also supports removing enchantments
							if (level > 0) {
								enchantments.put(enchantment, level);
							} else {
								enchantments.remove(enchantment);
							}
							setEnchantments(enchantments, stack);
							if (stack.is(Items.ENCHANTED_BOOK) && enchantments.isEmpty()) {
								stack = new ItemStack(Items.BOOK);
								CompoundTag compoundTag = itemStack.getTag();
								if (compoundTag != null) {
									stack.setTag(compoundTag.copy());
								}
							}
							// don't set this to the hand before, in case enchanting isn't successful
							livingEntity.setItemInHand(InteractionHand.MAIN_HAND, stack);
							++successes;
						} else if (collection.size() == 1) {
							throw ERROR_INCOMPATIBLE.create(itemStack.getItem().getName(itemStack).getString());
						}
					} else {
						if (enchantment.canEnchant(itemStack) && EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantments(itemStack).keySet(), enchantment)) {
							itemStack.enchant(enchantment, level);
							++successes;
						} else if (collection.size() == 1) {
							throw ERROR_INCOMPATIBLE.create(itemStack.getItem().getName(itemStack).getString());
						}
					}
				} else if (collection.size() == 1) {
					throw ERROR_NO_ITEM.create(livingEntity.getName().getString());
				}
			} else if (collection.size() == 1) {
				throw ERROR_NOT_LIVING_ENTITY.create(entity.getName().getString());
			}
		}
		if (successes == 0) {
			throw ERROR_NOTHING_HAPPENED.create();
		} else {
			if (collection.size() == 1) {
				commandSourceStack.sendSuccess(
						level > 0 || !UniversalEnchants.CONFIG.get(ServerConfig.class).fixEnchantCommand
								? Component.translatable("commands.enchant.success.single", enchantment.getFullname(level), collection.iterator().next().getDisplayName())
								: Component.translatable("commands.enchant.remove.success.single", getEnchantmentName(enchantment), collection.iterator().next().getDisplayName()), true
				);
			} else {
				commandSourceStack.sendSuccess(
						level > 0 || !UniversalEnchants.CONFIG.get(ServerConfig.class).fixEnchantCommand
							? Component.translatable("commands.enchant.success.multiple", enchantment.getFullname(level), collection.size())
							: Component.translatable("commands.enchant.remove.success.multiple", getEnchantmentName(enchantment), collection.size()), true);
			}

			return successes;
		}
	}

	private static void setEnchantments(Map<Enchantment, Integer> map, ItemStack itemStack) {
		// vanilla handles enchanted books much differently using EnchantedBookItem::addEnchantment
		// we don't want that, as it doesn't allow for overriding/removing existing enchantment levels
		ListTag list = new ListTag();
		for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
			Enchantment enchantment = entry.getKey();
			if (enchantment != null) {
				list.add(EnchantmentHelper.storeEnchantment(EnchantmentHelper.getEnchantmentId(enchantment), entry.getValue()));
			}
		}
		String enchantmentsKey = itemStack.is(Items.ENCHANTED_BOOK) ? EnchantedBookItem.TAG_STORED_ENCHANTMENTS : ItemStack.TAG_ENCH;
		if (list.isEmpty()) {
			itemStack.removeTagKey(enchantmentsKey);
		} else {
			itemStack.addTagElement(enchantmentsKey, list);
		}
	}

	private static Component getEnchantmentName(Enchantment enchantment) {
		MutableComponent mutableComponent = Component.translatable(enchantment.getDescriptionId());
		if (enchantment.isCurse()) {
			mutableComponent.withStyle(ChatFormatting.RED);
		} else {
			mutableComponent.withStyle(ChatFormatting.GRAY);
		}
		return mutableComponent;
	}
}
