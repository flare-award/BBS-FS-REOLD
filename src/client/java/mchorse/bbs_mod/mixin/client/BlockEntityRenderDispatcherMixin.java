package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.FilmEffects;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin
{
    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
    private static void onRenderMain(CallbackInfo info)
    {
        if (BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get())
        {
            info.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderToo(BlockEntity blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo info)
    {
        if (BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get())
        {
            info.cancel();

            return;
        }

        /* A behind-the-actors photo replays model blocks under itself earlier in
         * the frame; the regular draw here would put them back on top of it. */
        if (blockEntity instanceof ModelBlockEntity && FilmEffects.shouldHideModelBlocks())
        {
            info.cancel();
        }
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    public void onRenderEntity(CallbackInfoReturnable<Boolean> info)
    {
        if (BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get())
        {
            info.setReturnValue(false);
        }
    }
}