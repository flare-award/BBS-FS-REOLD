package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.IrliteL10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Translates the two IRLights light-model form panels (Point Light and
 *  Spotlight): every field label and toggle they build with
 *  {@code IKey.constant(...)} in their constructors. Without the addon the
 *  target classes never load and this mixin is never applied. */
@Mixin(targets =
{
    "qualet.irlite.client.ui.forms.editors.panels.UIPointLightFormPanel",
    "qualet.irlite.client.ui.forms.editors.panels.UISpotlightFormPanel"
})
public class IrliteLightFormPanelsL10nMixin
{
    private static final String IKEY_CONSTANT =
        "Lmchorse/bbs_mod/l10n/keys/IKey;constant(Ljava/lang/String;)Lmchorse/bbs_mod/l10n/keys/IKey;";

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
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
