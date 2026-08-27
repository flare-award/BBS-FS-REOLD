package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

/**
 * Color grading for the film's preview and export. Every slider is baked into the
 * export texture by {@link mchorse.bbs_mod.client.FilmEffects} the moment it moves,
 * so the preview on the left, the film panel behind the overlay and any recorded
 * video all show the exact same picture.
 */
public class UIFilmFiltersOverlayPanel extends UIFilmEffectsOverlayPanel
{
    public UIFilmFiltersOverlayPanel()
    {
        super(UIKeys.FILM_FILTERS_TITLE);

        UIExportPreview preview = new UIExportPreview();
        UIIcon reset = new UIIcon(Icons.REFRESH, (b) -> this.reset());

        reset.tooltip(UIKeys.FILM_FILTERS_RESET, Direction.LEFT);

        UIElement column = UI.column(4,
            this.createRow(UIKeys.FILM_FILTERS_BRIGHTNESS, BBSSettings.filmFilterBrightness, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_CONTRAST, BBSSettings.filmFilterContrast, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_SATURATION, BBSSettings.filmFilterSaturation, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_HUE, BBSSettings.filmFilterHue, -180D, 180D, 1D),
            this.createRow(UIKeys.FILM_FILTERS_TEMPERATURE, BBSSettings.filmFilterTemperature, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_GAMMA, BBSSettings.filmFilterGamma, BBSSettings.MIN_FILM_GAMMA, BBSSettings.MAX_FILM_GAMMA, 1D),
            this.createRow(UIKeys.FILM_FILTERS_SHARPNESS, BBSSettings.filmFilterSharpness, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_VIGNETTE, BBSSettings.filmFilterVignette, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_SEPIA, BBSSettings.filmFilterSepia, 0D, 100D, 100D)
        );

        preview.relative(this.content).xy(PADDING, PADDING).wh(PREVIEW_W, PREVIEW_H);
        column.relative(this.content).x(PREVIEW_W + PADDING * 2).y(PADDING).w(1F, -(PREVIEW_W + PADDING * 3));

        this.icons.add(reset);
        this.content.add(preview, column);
    }

    private void reset()
    {
        BBSSettings.filmFilterBrightness.set(0F);
        BBSSettings.filmFilterContrast.set(0F);
        BBSSettings.filmFilterSaturation.set(0F);
        BBSSettings.filmFilterHue.set(0F);
        BBSSettings.filmFilterTemperature.set(0F);
        BBSSettings.filmFilterGamma.set(1F);
        BBSSettings.filmFilterSharpness.set(0F);
        BBSSettings.filmFilterVignette.set(0F);
        BBSSettings.filmFilterSepia.set(0F);

        this.updateFields();
        UIUtils.playClick();
    }
}
