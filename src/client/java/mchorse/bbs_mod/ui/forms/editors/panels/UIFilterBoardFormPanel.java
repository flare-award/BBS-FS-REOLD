package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.FilterBoardForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

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
    private final UIToggle billboard;

    public UIFilterBoardFormPanel(UIForm editor)
    {
        super(editor);

        this.billboard = new UIToggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, false, (button) -> this.form.billboard.set(button.getValue()));

        this.options.add(this.billboard);
        this.options.add(UI.label(UIKeys.FILM_FILTERS_TITLE).marginTop(UIConstants.SECTION_GAP),
            this.createRow(UIKeys.FILM_FILTERS_BRIGHTNESS, (form) -> form.brightness, -100D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_CONTRAST, (form) -> form.contrast, -100D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_SATURATION, (form) -> form.filterSaturation, -100D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_HUE, (form) -> form.filterHue, -180D, 180D, 1D, false),
            this.createRow(UIKeys.FILM_FILTERS_TEMPERATURE, (form) -> form.temperature, -100D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_GAMMA, (form) -> form.gamma, BBSSettings.MIN_FILM_GAMMA, BBSSettings.MAX_FILM_GAMMA, 1D, false),
            this.createRow(UIKeys.FILM_FILTERS_SHARPNESS, (form) -> form.sharpness, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_VIGNETTE, (form) -> form.vignette, -100D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_SEPIA, (form) -> form.sepia, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_GRAIN, (form) -> form.grain, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_ABERRATION, (form) -> form.aberration, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_INVERT, (form) -> form.invert, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_POSTERIZE, (form) -> form.posterize, 0D, BBSSettings.MAX_FILM_POSTERIZE, 1D, true),
            this.createRow(UIKeys.FILM_FILTERS_PIXELATE, (form) -> form.pixelate, 0D, BBSSettings.MAX_FILM_PIXELATE, 1D, true),
            this.createRow(UIKeys.FILM_FILTERS_BLOOM, (form) -> form.bloom, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_RADIAL, (form) -> form.radial, 0D, 100D, 100D, false),
            this.createRow(UIKeys.FILM_FILTERS_VHS, (form) -> form.vhs, 0D, 100D, 100D, false)
        );
    }

    private UIElement createRow(IKey label, Function<FilterBoardForm, ValueFloat> value, double min, double max, double uiScale, boolean integer)
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

        UIElement row = UI.row(4, UI.label(label).labelAnchor(0, 0.5F), slider);

        row.h(20);

        return row;
    }

    @Override
    public void startEdit(FilterBoardForm form)
    {
        super.startEdit(form);
        this.billboard.setValue(form.billboard.get());

        for (Runnable updater : this.filterUpdaters)
        {
            updater.run();
        }
    }
}
