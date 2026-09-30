package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.IrliteL10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Translates the collapsible section headers of the IRLights light-model
 *  forms. {@code spaced(...)} delegates to {@code section(...)}, so hooking
 *  the one {@code IKey.constant(...)} call in {@code section} covers both
 *  entry points. Without the addon the target class never loads and this
 *  mixin is never applied. */
@Mixin(targets = "qualet.irlite.client.ui.forms.editors.panels.IrliteFormSections")
public class IrliteFormSectionsL10nMixin
{
    private static final String IKEY_CONSTANT =
        "Lmchorse/bbs_mod/l10n/keys/IKey;constant(Ljava/lang/String;)Lmchorse/bbs_mod/l10n/keys/IKey;";

    @ModifyArg(method = "section", at = @At(value = "INVOKE", target = IKEY_CONSTANT), index = 0, remap = false)
    private static String irliteRu(String title)
    {
        try
        {
            return IrliteL10n.translate(title, BBSModClient.getLanguageKey());
        }
        catch (Throwable t)
        {
            return title;
        }
    }
}
