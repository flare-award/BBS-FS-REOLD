package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the film effect overlays (color grading filters and the photo overlay):
 * a live export preview on the left, labeled sliders on the right. Sliders write
 * straight into their settings on every change, so the preview - and the export -
 * follow the drag in real time rather than waiting for the mouse to let go.
 */
public abstract class UIFilmEffectsOverlayPanel extends UIOverlayPanel
{
    public static final int WIDTH = 560;
    public static final int HEIGHT = 252;

    protected static final int PADDING = 6;
    protected static final int PREVIEW_W = 320;
    protected static final int PREVIEW_H = 180;

    /** Refreshers that pull every slider back in sync with its setting. */
    protected final List<Runnable> updaters = new ArrayList<>();

    public UIFilmEffectsOverlayPanel(IKey title)
    {
        super(title);
    }

    /**
     * A row of label and slider bound to a setting. The setting stores real units
     * while the slider shows them scaled (usually to percent), so the conversion
     * lives here and nowhere else.
     */
    protected UIElement createRow(IKey label, ValueFloat value, double min, double max, double uiScale)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) -> value.set((float) (v / uiScale)));

        slider.limit(min, max);
        slider.setValue(value.get() * uiScale);
        this.updaters.add(() -> slider.setValue(value.get() * uiScale));

        UIElement row = UI.row(4, UI.label(label).labelAnchor(0, 0.5F), slider);

        row.h(20);

        return row;
    }

    protected void updateFields()
    {
        for (Runnable updater : this.updaters)
        {
            updater.run();
        }
    }
}
