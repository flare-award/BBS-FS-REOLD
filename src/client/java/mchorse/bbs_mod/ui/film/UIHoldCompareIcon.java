package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Consumer;

/**
 * Hold-to-compare icon of the film effect overlays: while held down it reports
 * {@code true} (the panel bypasses its effect for a before/after look), and
 * {@code false} again the moment the mouse lets go.
 */
public class UIHoldCompareIcon extends UIIcon
{
    private final Consumer<Boolean> callback;

    public UIHoldCompareIcon(Consumer<Boolean> callback)
    {
        super(Icons.VISIBLE, null);

        this.callback = callback;
    }

    @Override
    protected void click(int mouseButton)
    {
        this.callback.accept(true);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.callback.accept(false);

        return super.subMouseReleased(context);
    }
}
