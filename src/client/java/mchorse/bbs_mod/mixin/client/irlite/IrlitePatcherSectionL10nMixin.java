package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.IrliteL10n;
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
    private static String irliteRuAppend(String label)
    {
        return irliteRu(label);
    }

    @ModifyArg(method = "headerRow", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private static String irliteRuHeader(String label)
    {
        return irliteRu(label);
    }

    @ModifyArg(method = "setMeta", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private static String irliteRuMeta(String label)
    {
        return irliteRu(label);
    }

    @ModifyArg(method = "setStatus", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private static String irliteRuStatus(String label)
    {
        return irliteRu(label);
    }

    private static String irliteRu(String label)
    {
        try
        {
            return IrliteL10n.translate(label, BBSModClient.getLanguageKey());
        }
        catch (Throwable t)
        {
            return label;
        }
    }
}
