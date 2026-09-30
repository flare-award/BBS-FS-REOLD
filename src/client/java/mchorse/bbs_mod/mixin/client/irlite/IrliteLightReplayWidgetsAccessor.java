package mchorse.bbs_mod.mixin.client.irlite;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Field accessors for the IRLights replay-filter row, applied by Mixin
 *  directly to the target (standalone interface, registered in
 *  bbs.client.mixins.json — same pattern as the other accessors in here).
 *  {@link IrliteLightReplayWidgetsL10nMixin} casts the merged instance to
 *  it. Without the addon the target class never loads and neither the
 *  interface nor the mixin apply. */
@Mixin(targets = "qualet.irlite.client.ui.forms.editors.panels.LightReplayWidgets")
public interface IrliteLightReplayWidgetsAccessor
{
    @Accessor("summary")
    UIElement irliteSummary();

    @Accessor("status")
    UIElement irliteStatus();
}
