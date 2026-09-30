package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.IrliteL10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Translates the display names of the two IRLights light models ("Point
 *  light" / "Spotlight") as reported by {@code getDefaultDisplayName()} —
 *  that string is what the form picker and the form headers show. Without
 *  the addon the target classes never load and this mixin is never
 *  applied. */
@Mixin(targets =
{
    "qualet.irlite.forms.PointLightForm",
    "qualet.irlite.forms.SpotlightForm"
})
public class IrliteLightFormsL10nMixin
{
    /* require = 0: the mixin applies to both form classes, and each
     * constant exists in only one of them — the other class simply has no
     * matching target, which must not be an error */
    @ModifyConstant(method = "getDefaultDisplayName", constant = @Constant(string = "Point light"), require = 0, remap = false)
    private static String irliteRuPointLight(String name)
    {
        return irliteRu(name);
    }

    @ModifyConstant(method = "getDefaultDisplayName", constant = @Constant(string = "Spotlight"), require = 0, remap = false)
    private static String irliteRuSpotlight(String name)
    {
        return irliteRu(name);
    }

    private static String irliteRu(String name)
    {
        try
        {
            return IrliteL10n.translate(name, BBSModClient.getLanguageKey());
        }
        catch (Throwable t)
        {
            return name;
        }
    }
}
