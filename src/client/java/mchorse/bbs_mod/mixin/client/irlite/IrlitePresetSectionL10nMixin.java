package mchorse.bbs_mod.mixin.client.irlite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Translates the "Presets" header, the Quality and Beam style rows with
 *  their tooltips, and the preset option labels that the IRLights addon
 *  hardcodes with {@code IKey.constant(...)} in UIPresetSection. Without the
 *  addon the target class never loads and this mixin is never applied. */
@Mixin(targets = "qualet.irlite.client.ui.presets.UIPresetSection")
public class IrlitePresetSectionL10nMixin
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
    private String irliteRu(String label)
    {
        return IrliteL10n.translate(label);
    }
}
