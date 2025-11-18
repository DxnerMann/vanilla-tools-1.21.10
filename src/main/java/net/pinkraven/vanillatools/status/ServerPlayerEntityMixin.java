package net.pinkraven.vanillatools.status;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Shadow
    public abstract Text getCustomName();

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void customDisplayName(CallbackInfoReturnable<Text> cir) {
        Text customName = this.getCustomName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }

    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void customPlayerListName(CallbackInfoReturnable<Text> cir) {
        Text customName = this.getCustomName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }
}