package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.utils.IrliteL10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Translates the mode labels (LOW/MEDIUM/.../ALL/ENTITIES/BLOCKS) that the
 *  IRLights addon registers with {@code IKey.constant(...)} for its
 *  shadow_quality and outline_target settings. The target class only exists
 *  while the IRLights addon is installed; without it this mixin is never
 *  applied and nothing happens. */
@Mixin(targets = "qualet.irlite.IrlightsAddon")
public class IrliteAddonL10nMixin
{
    @ModifyArg(
        method = "build",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/l10n/keys/IKey;constant(Ljava/lang/String;)Lmchorse/bbs_mod/l10n/keys/IKey;"
        ),
        index = 0,
        remap = false
    )
    private static String irliteRu(String label)
    {
        return IrliteL10n.translate(label);
    }
}
