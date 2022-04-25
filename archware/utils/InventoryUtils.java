package archware.utils;

import archware.utils.timers.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static archware.module.Module.mc;

public final class InventoryUtils {
    public static final int INCLUDE_ARMOR_BEGIN = 5;
    public static final int EXCLUDE_ARMOR_BEGIN = 9;
    public static final int ONLY_HOT_BAR_BEGIN = 36;

    public static final int END = 45;

    private static final List<Integer> BAD_EFFECTS_IDS = Arrays.asList(
            Potion.poison.id, Potion.weakness.id, Potion.wither.id,
            Potion.blindness.id, Potion.digSlowdown.id, Potion.harm.id);

    public static void click(int slot, int mouseButton, boolean shiftClick) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, mouseButton, shiftClick ? 1 : 0, mc.thePlayer);
    }

    public static void drop(int slot) {
        mc.playerController.windowClick(0, slot, 1, 4, mc.thePlayer);
    }

    public static void swap(int slot, int hSlot) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, hSlot, 2, mc.thePlayer);
    }

    public static boolean isBuffPotion(ItemStack stack) {
        ItemPotion potion = (ItemPotion) stack.getItem();
        List<PotionEffect> effects = potion.getEffects(stack);
        if (effects.size() < 1)
            return false;

        for (PotionEffect effect : effects) {
            if (BAD_EFFECTS_IDS.contains(effect.getPotionID()))
                return false;
        }

        return true;
    }

    public static void windowClick(int windowId, int slotId, int mouseButtonClicked, ClickType mode) {
        Wrapper.getPlayerController().windowClick(windowId, slotId, mouseButtonClicked, mode.ordinal(), Wrapper.getPlayer());
    }

    public static void windowClick(int slotId, int mouseButtonClicked, ClickType mode) {
        Wrapper.getPlayerController().windowClick(Wrapper.getPlayer().inventoryContainer.windowId, slotId,
                mouseButtonClicked, mode.ordinal(), Wrapper.getPlayer());
    }

    public enum ClickType {
        // if mouseButtonClicked is 0 `DROP_ITEM` will drop 1
        // item from the stack else if it is 1 it will drop the entire stack
        CLICK, SHIFT_CLICK, SWAP_WITH_HOT_BAR_SLOT, PLACEHOLDER, DROP_ITEM
    }

    public static boolean isValid(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemBlock) {
            return isGoodBlockStack(stack);
        } else if (stack.getItem() instanceof ItemSword) {
            return isBestSword(stack);
        } else if (stack.getItem() instanceof ItemTool) {
            return isBestTool(stack);
        } else if (stack.getItem() instanceof ItemArmor) {
            return isBestArmor(stack);
        } else if (stack.getItem() instanceof ItemPotion) {
            return isBuffPotion(stack);
        } else if (stack.getItem() instanceof ItemFood) {
            return isGoodFood(stack);
        } else if (stack.getItem() instanceof ItemBow) {
            return isBestBow(stack);
        } else return isGoodItem(stack);
    }

    public static boolean isGoodFood(ItemStack stack) {
        ItemFood food = (ItemFood) stack.getItem();

        if (food instanceof ItemAppleGold)
            return true;

        return food.getHealAmount(stack) >= 4 && food.getSaturationModifier(stack) >= 0.3F;
    }

    public static boolean isBestBow(ItemStack itemStack) {
        AtomicDouble bestBowDmg = new AtomicDouble(-1.0D);
        AtomicReference<ItemStack> bestBow = new AtomicReference<>(null);

        Wrapper.forEachInventorySlot(InventoryUtils.EXCLUDE_ARMOR_BEGIN, InventoryUtils.END, ((slot, stack) -> {
            if (stack.getItem() instanceof ItemBow) {
                double damage = getBowDamage(stack);

                if (damage > bestBowDmg.get()) {
                    bestBow.set(stack);
                    bestBowDmg.set(damage);
                }
            }
        }));

        return itemStack == bestBow.get() ||
                getBowDamage(itemStack) > bestBowDmg.get();
    }

    public static double getBowDamage(ItemStack stack) {
        double damage = 0.0D;

        if (stack.getItem() instanceof ItemBow && stack.isItemEnchanted())
            damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);

        return damage;
    }

    public static boolean isGoodItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemBucket)
            if (((ItemBucket) item).isFull != Blocks.flowing_water)
                return false;

        return !(item instanceof ItemExpBottle) && !(item instanceof ItemFishingRod) &&
                !(item instanceof ItemEgg) && !(item instanceof ItemSnowball) &&
                !(item instanceof ItemSkull) && !(item instanceof ItemBucket);
    }

    public static double getDamageReduction(ItemStack stack) {
        double reduction = 0.0;

        ItemArmor armor = (ItemArmor) stack.getItem();

        reduction += armor.damageReduceAmount;

        if (stack.isItemEnchanted())
            reduction += EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 0.25D;

        return reduction;
    }

    public static void openInventory() {
        Wrapper.sendPacketDirect(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
    }

    public static void closeInventory() {
        Wrapper.sendPacketDirect(new C0DPacketCloseWindow(Wrapper.getPlayer().inventoryContainer.windowId));
    }

    public static boolean isValidBlock(Block block, boolean toPlace) {
        if (block instanceof BlockContainer || !block.isFullBlock() || !block.isFullCube() || (toPlace && block instanceof BlockFalling))
            return false;
        Material material = block.getMaterial();
        return !material.isLiquid() && material.isSolid();
    }

    public static boolean isGoodBlockStack(ItemStack stack) {
        if (stack.stackSize < 1)
            return false;
        return isValidBlock(Block.getBlockFromItem(stack.getItem()), true);
    }

    public static int getToolType(ItemStack stack) {
        ItemTool tool = (ItemTool) stack.getItem();
        if (tool instanceof ItemPickaxe)
            return 0;
        else if (tool instanceof ItemAxe)
            return 1;
        else if (tool instanceof ItemSpade)
            return 2;
        else
            return -1;
    }

    public static boolean isBestTool(ItemStack itemStack) {
        final int type = getToolType(itemStack);

        AtomicReference<Tool> bestTool = new AtomicReference<>(new Tool(-1, -1, null));

        Wrapper.forEachInventorySlot(InventoryUtils.EXCLUDE_ARMOR_BEGIN, InventoryUtils.END, ((slot, stack) -> {
            if (stack.getItem() instanceof ItemTool && type == getToolType(stack)) {
                double efficiency = getToolEfficiency(stack);
                if (efficiency > bestTool.get().getEfficiency())
                    bestTool.set(new Tool(slot, efficiency, stack));
            }
        }));

        return bestTool.get().getStack() == itemStack ||
                bestTool.get().getEfficiency() < getToolEfficiency(itemStack);
    }

    public static boolean isBestSword(ItemStack itemStack) {
        AtomicDouble damage = new AtomicDouble(0.0);
        AtomicReference<ItemStack> bestStack = new AtomicReference<>(null);

        Wrapper.forEachInventorySlot(InventoryUtils.EXCLUDE_ARMOR_BEGIN, InventoryUtils.END, (slot, stack) -> {
            if (stack.getItem() instanceof ItemSword) {
                double newDamage = getItemDamage(stack);

                if (newDamage > damage.get()) {
                    damage.set(newDamage);
                    bestStack.set(stack);
                }
            }
        });

        return bestStack.get() == itemStack || damage.get() < getItemDamage(itemStack);
    }

    public static boolean isBestArmor(ItemStack itemStack) {
        ItemArmor itemArmor = (ItemArmor) itemStack.getItem();
        AtomicDouble reduction = new AtomicDouble(0.0);
        AtomicReference<ItemStack> bestStack = new AtomicReference<>(null);
        Wrapper.forEachInventorySlot(InventoryUtils.INCLUDE_ARMOR_BEGIN, InventoryUtils.END, ((slot, stack) -> {
            if (stack.getItem() instanceof ItemArmor) {
                ItemArmor stackArmor = (ItemArmor) stack.getItem();
                if (stackArmor.armorType == itemArmor.armorType) {
                    double newReduction = getDamageReduction(stack);

                    if (newReduction > reduction.get()) {
                        reduction.set(newReduction);
                        bestStack.set(stack);
                    }
                }
            }
        }));

        return bestStack.get() == itemStack ||
                reduction.get() < getDamageReduction(itemStack);
    }

    public static double getItemDamage(ItemStack stack) {
        double damage = 0.0;

        final Multimap<String, AttributeModifier> attributeModifierMap = stack.getAttributeModifiers();

        for (String attributeName : attributeModifierMap.keySet()) {
            if (attributeName.equals("generic.attackDamage")) {
                Iterator<AttributeModifier> attributeModifiers = attributeModifierMap.get(attributeName).iterator();
                if (attributeModifiers.hasNext())
                    damage += attributeModifiers.next().getAmount();
                break;
            }
        }

        if (stack.isItemEnchanted()) {
            damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
            damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25;
        }

        return damage;
    }

    public static float getToolEfficiency(ItemStack itemStack) {
        ItemTool tool = (ItemTool) itemStack.getItem();

        float efficiency = tool.getEfficiencyOnProperMaterial();

        int lvl = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);

        if (efficiency > 1.0F && lvl > 0)
            efficiency += lvl * lvl + 1;

        return efficiency;
    }

    private static class Tool {
        private final int slot;
        private final double efficiency;
        private final ItemStack stack;

        public Tool(int slot, double efficiency, ItemStack stack) {
            this.slot = slot;
            this.efficiency = efficiency;
            this.stack = stack;
        }

        public int getSlot() {
            return slot;
        }

        public double getEfficiency() {
            return efficiency;
        }

        public ItemStack getStack() {
            return stack;
        }
    }
}
