package mchorse.bbs_mod.mixin.client.irlite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Translates the shader-patcher page of the IRLights addon: the section
 *  headers, button labels and the static status messages. Lines that embed
 *  pack or patch names stay in English — they are built at runtime. Without
 *  the addon the target class never loads and this mixin is never applied. */
@Mixin(targets = "qualet.irlite.client.ui.patcher.UIPatcherSection")
public class IrlitePatcherSectionL10nMixin
{
    private static final String IKEY_CONSTANT =
        "Lmchorse/bbs_mod/l10n/keys/IKey;constant(Ljava/lang/String;)Lmchorse/bbs_mod/l10n/keys/IKey;";

    @ModifyArg(method = "append", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private String irliteRuAppend(String label)
    {
        return IrliteL10n.translate(label);
    }

    @ModifyArg(method = "headerRow", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private String irliteRuHeader(String label)
    {
        return IrliteL10n.translate(label);
    }

    @ModifyArg(method = "setMeta", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private String irliteRuMeta(String label)
    {
        return IrliteL10n.translate(label);
    }

    @ModifyArg(method = "setStatus", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private String irliteRuStatus(String label)
    {
        return IrliteL10n.translate(label);
    }
}
