package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Base of the film effect overlays (color grading filters and the photo overlay):
 * a live export preview on the left, labeled sliders on the right. Sliders write
 * straight into their targets on every change, so the preview - and the export -
 * follow the drag in real time rather than waiting for the mouse to let go.
 */
public abstract class UIFilmEffectsOverlayPanel extends UIOverlayPanel
{
    public static final int WIDTH = 560;
    public static final int HEIGHT = 252;

    protected static final int PADDING = 6;
    protected static final int PREVIEW_W = 320;
    protected static final int PREVIEW_H = 180;

    /** Refreshers that pull every slider back in sync with its value. */
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
        return this.createRow(label, () -> value.get(), (v) -> value.set((float) v), min, max, uiScale, false);
    }

    /** Same as {@link #createRow(IKey, ValueFloat, double, double, double)}, but whole numbers only. */
    protected UIElement createIntegerRow(IKey label, ValueFloat value, double min, double max)
    {
        return this.createRow(label, () -> value.get(), (v) -> value.set((float) v), min, max, 1D, true);
    }

    /**
     * A row of label and slider bound to arbitrary get/set functions - the photo
     * overlay uses these to edit whichever layer is selected at the moment.
     */
    protected UIElement createRow(IKey label, DoubleSupplier getter, DoubleConsumer setter, double min, double max, double uiScale, boolean integer)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) -> setter.accept(v / uiScale));

        slider.limit(min, max);

        if (integer)
        {
            slider.integer();
        }

        slider.setValue(getter.getAsDouble() * uiScale);
        this.updaters.add(() -> slider.setValue(getter.getAsDouble() * uiScale));

        UIElement row = UI.row(4, UI.label(label).labelAnchor(0, 0.5F), slider);

        row.h(20);

        return row;
    }

    /**
     * A row of label and options button bound to a setting that stores a small
     * whole number - the button cycles through the given option labels.
     */
    protected UIElement createOptionsRow(IKey label, ValueFloat value, IKey... options)
    {
        UICirculate button = new UICirculate((b) -> value.set((float) b.getValue()));

        for (IKey option : options)
        {
            button.addLabel(option);
        }

        button.setValue(this.optionIndex(value, options.length));
        this.updaters.add(() -> button.setValue(this.optionIndex(value, options.length)));

        UIElement row = UI.row(4, UI.label(label).labelAnchor(0, 0.5F), button);

        row.h(20);

        return row;
    }

    private int optionIndex(ValueFloat value, int count)
    {
        return Math.max(0, Math.min(count - 1, Math.round(value.get())));
    }

    protected void updateFields()
    {
        for (Runnable updater : this.updaters)
        {
            updater.run();
        }
    }
}
