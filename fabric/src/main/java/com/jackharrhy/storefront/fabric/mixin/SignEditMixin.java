package com.jackharrhy.storefront.fabric.mixin;

import com.jackharrhy.storefront.fabric.FabricStorefront;

import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(SignBlockEntity.class)
abstract class SignEditMixin {
    @Unique private boolean storefront$accepted;

    @Inject(method = "updateSignText", at = @At("HEAD"), cancellable = true)
    private void storefront$authorize(Player player, boolean front, List<FilteredText> lines, CallbackInfo callback) {
        var sign = (SignBlockEntity) (Object) this;
        storefront$accepted = front && !sign.isWaxed() && player.getUUID().equals(sign.getPlayerWhoMayEdit());
        if (front && FabricStorefront.instance != null && !FabricStorefront.instance.mayEdit(player, sign)) {
            storefront$accepted = false;
            callback.cancel();
        }
    }

    @Inject(method = "updateSignText", at = @At("TAIL"))
    private void storefront$save(Player player, boolean front, List<FilteredText> lines, CallbackInfo callback) {
        if (storefront$accepted && FabricStorefront.instance != null) {
            FabricStorefront.instance.save(player, (SignBlockEntity) (Object) this);
        }
    }
}
