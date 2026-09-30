package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.utils.IrliteL10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Translates the replay-filter row ("Affects" section) of the IRLights
 *  light-model forms. The static labels and tooltip go through
 *  {@code IKey.constant(...)} in the constructor; the two dynamic status
 *  labels are lambdas, so they get wrapped at the end of the constructor —
 *  the "Default list:" line keeps the user's replay names and only its
 *  prefix is translated. Field accessors live in
 *  {@link IrliteLightReplayWidgetsAccessor}. Without the addon the target
 *  class never loads and this mixin is never applied. */
@Mixin(targets = "qualet.irlite.client.ui.forms.editors.panels.LightReplayWidgets")
public class IrliteLightReplayWidgetsL10nMixin implements IrliteLightReplayWidgetsAccessor
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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void irliteRuDynamicLabels(CallbackInfo ci)
    {
        try
        {
            String language = BBSModClient.getLanguageKey();

            UIElement summaryElement = this.irliteSummary();

            if (summaryElement instanceof UILabel summary)
            {
                IKey originalSummary = summary.label;

                summary.label = () -> IrliteL10n.translatePrefix(originalSummary.get(), "Default list: ", language);
            }

            UIElement statusElement = this.irliteStatus();

            if (statusElement instanceof UILabel status)
            {
                IKey originalStatus = status.label;

                status.label = () -> IrliteL10n.translate(originalStatus.get(), language);
            }
        }
        catch (Throwable t)
        {
            /* never break the addon's panel because of l10n */
        }
    }
}
