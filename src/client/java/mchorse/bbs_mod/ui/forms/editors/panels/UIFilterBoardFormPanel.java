package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.FilterBoardForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * FilterBoard geometry is controlled by the regular transform and billboard
 * toggle. The remaining controls are the filters local to this form; texture,
 * tint, shading, distortion, fisheye and flip are intentionally not part of it.
 */
public class UIFilterBoardFormPanel extends UIFormPanel<FilterBoardForm>
{
    private final List<Runnable> filterUpdaters = new ArrayList<>();
    private final List<Runnable> filterResetters = new ArrayList<>();
    private final UIToggle billboard;
    private final UICirculate hslColor;
    private int hslColorIndex;

    public UIFilterBoardFormPanel(UIForm editor)
    {
        super(editor);

        this.billboard = new UIToggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, false, (button) -> this.form.billboard.set(button.getValue()));
        this.hslColor = new UICirculate((button) ->
        {
            this.hslColorIndex = button.getValue();
            this.updateFields();
        });

        for (IKey color : UIKeys.FILM_FILTERS_HSL_COLORS)
        {
            this.hslColor.addLabel(color);
        }

        this.hslColor.tooltip(UIKeys.FILM_FILTERS_HSL);

        UIIcon resetAll = new UIIcon(Icons.REFRESH, (button) -> this.resetAllFilters());
        resetAll.tooltip(UIKeys.FILM_FILTERS_RESET);

        this.options.add(this.billboard,
            UI.row(4, UI.label(UIKeys.FILM_FILTERS_TITLE).labelAnchor(0, 0.5F), resetAll).h(UIConstants.CONTROL_HEIGHT),
            this.createRow(UIKeys.FILM_FILTERS_BRIGHTNESS, (form) -> form.brightness, -100D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_CONTRAST, (form) -> form.contrast, -100D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_SATURATION, (form) -> form.filterSaturation, -100D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_HUE, (form) -> form.filterHue, -180D, 180D, 1D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_TEMPERATURE, (form) -> form.temperature, -100D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_GAMMA, (form) -> form.gamma, BBSSettings.MIN_FILM_GAMMA, BBSSettings.MAX_FILM_GAMMA, 1D, false, 1F),
            this.createRow(UIKeys.FILM_FILTERS_SHARPNESS, (form) -> form.sharpness, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_VIGNETTE, (form) -> form.vignette, -100D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_SEPIA, (form) -> form.sepia, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_GRAIN, (form) -> form.grain, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_ABERRATION, (form) -> form.aberration, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_INVERT, (form) -> form.invert, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_POSTERIZE, (form) -> form.posterize, 0D, BBSSettings.MAX_FILM_POSTERIZE, 1D, true, 0F),
            this.createRow(UIKeys.FILM_FILTERS_PIXELATE, (form) -> form.pixelate, 0D, BBSSettings.MAX_FILM_PIXELATE, 1D, true, 0F),
            this.createRow(UIKeys.FILM_FILTERS_BLOOM, (form) -> form.bloom, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_RADIAL, (form) -> form.radial, 0D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_VHS, (form) -> form.vhs, 0D, 100D, 100D, false, 0F),
            UI.row(4, UI.label(UIKeys.FILM_FILTERS_HSL_COLOR).labelAnchor(0, 0.5F), this.hslColor).h(UIConstants.CONTROL_HEIGHT),
            this.createRow(UIKeys.FILM_FILTERS_HSL_HUE, (form) -> form.hslHue[this.hslColorIndex], -180D, 180D, 1D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_HSL_SATURATION, (form) -> form.hslSaturation[this.hslColorIndex], -100D, 100D, 100D, false, 0F),
            this.createRow(UIKeys.FILM_FILTERS_HSL_LIGHTNESS, (form) -> form.hslLightness[this.hslColorIndex], -100D, 100D, 100D, false, 0F)
        );
    }

    private UIElement createRow(IKey label, Function<FilterBoardForm, ValueFloat> value, double min, double max, double uiScale, boolean integer, float defaultValue)
    {
        UISliderTrackpad slider = new UISliderTrackpad((number) ->
        {
            if (this.form != null)
            {
                value.apply(this.form).set((float) (number / uiScale));
            }
        });
        slider.limit(min, max);

        if (integer)
        {
            slider.integer();
        }

        this.filterUpdaters.add(() ->
        {
            if (this.form != null)
            {
                slider.setValue(value.apply(this.form).get() * uiScale);
            }
        });

        this.filterResetters.add(() ->
        {
            if (this.form != null)
            {
                value.apply(this.form).set(defaultValue);
            }
        });

        UIIcon reset = new UIIcon(Icons.REFRESH, (button) ->
        {
            if (this.form != null)
            {
                value.apply(this.form).set(defaultValue);
                this.updateFields();
            }
        });
        reset.tooltip(UIKeys.FILM_FILTERS_RESET_ONE);

        UIElement row = UI.row(4, UI.label(label).labelAnchor(0, 0.5F), slider, reset);

        row.h(20);

        return row;
    }

    private void resetAllFilters()
    {
        if (this.form == null)
        {
            return;
        }

        for (Runnable resetter : this.filterResetters)
        {
            resetter.run();
        }

        for (int i = 0; i < BBSSettings.HSL_COLOR_COUNT; i++)
        {
            this.form.hslHue[i].set(0F);
            this.form.hslSaturation[i].set(0F);
            this.form.hslLightness[i].set(0F);
        }

        this.updateFields();
    }

    private void updateFields()
    {
        for (Runnable updater : this.filterUpdaters)
        {
            updater.run();
        }
    }

    @Override
    public void startEdit(FilterBoardForm form)
    {
        super.startEdit(form);
        this.billboard.setValue(form.billboard.get());
        this.hslColorIndex = 0;
        this.hslColor.setValue(0);

        this.updateFields();
    }
}
