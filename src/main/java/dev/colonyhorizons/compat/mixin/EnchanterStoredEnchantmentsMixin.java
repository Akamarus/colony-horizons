// SPDX-License-Identifier: GPL-3.0-only
// Adapts MineColonies code by LDTTeam contributors; see NOTICE.
// Modified by Akamarus / Colony Horizons, 2026-10-11.
package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkEnchanter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MineColonies 1.1.1403 reads direct enchantments for books whose loot uses stored enchantments. */
@Mixin(value = EntityAIWorkEnchanter.class, remap = false)
public abstract class EnchanterStoredEnchantmentsMixin {
    @Inject(method = "getEnchantedBookLevel", at = @At("HEAD"), cancellable = true, require = 1)
    private static void colonyHorizons$storedBookCost(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            // Keep the native highest-enchantment-level cost; only correct its component source.
            int highestLevel = 0;
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
                highestLevel = Math.max(highestLevel, entry.getIntValue());
            }
            cir.setReturnValue(highestLevel);
        }
    }
}
