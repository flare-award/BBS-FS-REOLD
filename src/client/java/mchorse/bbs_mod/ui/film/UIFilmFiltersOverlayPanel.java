package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FilmEffects;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.presets.PresetManager;

/**
 * Color grading for the film's preview and export. Every slider is baked into the
 * export texture by {@link FilmEffects} the moment it moves, so the preview on the
 * left, the film panel behind the overlay and any recorded video all show the exact
 * same picture. The whole slider set can be stored as a preset, and holding the eye
 * icon flips the preview back to the untouched frame for a quick before/after check.
 */
public class UIFilmFiltersOverlayPanel extends UIFilmEffectsOverlayPanel
{
    private final UICopyPasteController presetsController;

    public UIFilmFiltersOverlayPanel()
    {
        super(UIKeys.FILM_FILTERS_TITLE);

        this.presetsController = new UICopyPasteController(PresetManager.FILTERS, "_CopyFilmFilters")
            .supplier(this::serializeFilters)
            .consumer((data, mouseX, mouseY) -> this.pasteFilters(data));

        UIExportPreview preview = new UIExportPreview();
        UIIcon presets = new UIIcon(Icons.MORE, (b) -> this.openPresets());
        UIIcon compare = new UIHoldCompareIcon(FilmEffects::setShowOriginal);
        UIIcon reset = new UIIcon(Icons.REFRESH, (b) -> this.reset());

        presets.tooltip(UIKeys.FILM_FILTERS_PRESETS, Direction.LEFT);
        compare.tooltip(UIKeys.FILM_FILTERS_COMPARE, Direction.LEFT);
        reset.tooltip(UIKeys.FILM_FILTERS_RESET, Direction.LEFT);

        UIScrollView column = UI.scrollView(4, PADDING,
            this.createRow(UIKeys.FILM_FILTERS_BRIGHTNESS, BBSSettings.filmFilterBrightness, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_CONTRAST, BBSSettings.filmFilterContrast, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_SATURATION, BBSSettings.filmFilterSaturation, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_HUE, BBSSettings.filmFilterHue, -180D, 180D, 1D),
            this.createRow(UIKeys.FILM_FILTERS_TEMPERATURE, BBSSettings.filmFilterTemperature, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_GAMMA, BBSSettings.filmFilterGamma, BBSSettings.MIN_FILM_GAMMA, BBSSettings.MAX_FILM_GAMMA, 1D),
            this.createRow(UIKeys.FILM_FILTERS_SHARPNESS, BBSSettings.filmFilterSharpness, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_VIGNETTE, BBSSettings.filmFilterVignette, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_SEPIA, BBSSettings.filmFilterSepia, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_GRAIN, BBSSettings.filmFilterGrain, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_ABERRATION, BBSSettings.filmFilterAberration, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_INVERT, BBSSettings.filmFilterInvert, 0D, 100D, 100D),
            this.createIntegerRow(UIKeys.FILM_FILTERS_POSTERIZE, BBSSettings.filmFilterPosterize, 0D, BBSSettings.MAX_FILM_POSTERIZE),
            this.createIntegerRow(UIKeys.FILM_FILTERS_PIXELATE, BBSSettings.filmFilterPixelate, 0D, BBSSettings.MAX_FILM_PIXELATE),
            this.createRow(UIKeys.FILM_FILTERS_DISTORTION, BBSSettings.filmFilterDistortion, -100D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_BLOOM, BBSSettings.filmFilterBloom, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_RADIAL, BBSSettings.filmFilterRadial, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_VHS, BBSSettings.filmFilterVhs, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_FILTERS_FISHEYE, BBSSettings.filmFilterFisheye, -100D, 100D, 100D),
            this.createOptionsRow(UIKeys.FILM_FILTERS_FLIP, BBSSettings.filmFilterFlip, UIKeys.FILM_FILTERS_FLIP_NONE, UIKeys.FILM_FILTERS_FLIP_VERTICAL, UIKeys.FILM_FILTERS_FLIP_HORIZONTAL)
        );

        preview.relative(this.content).xy(PADDING, PADDING).wh(PREVIEW_W, PREVIEW_H);
        column.relative(this.content).x(PREVIEW_W + PADDING).y(0).w(1F, -(PREVIEW_W + PADDING)).h(1F);

        this.icons.add(presets);
        this.icons.add(compare);
        this.icons.add(reset);
        this.content.add(preview, column);
    }

    @Override
    public void onClose()
    {
        /* Never leave the compare bypass stuck on when the overlay goes away */
        FilmEffects.setShowOriginal(false);

        super.onClose();
    }

    private void openPresets()
    {
        UIContext context = this.getContext();

        this.presetsController.openPresets(context, context.mouseX, context.mouseY);
    }

    private MapType serializeFilters()
    {
        return FilmEffects.serializeFilters();
    }

    /** Settings clamp on set, so a hand-edited preset can't push values out of range. */
    private void pasteFilters(MapType data)
    {
        if (data == null)
        {
            return;
        }

        FilmEffects.applyFilterData(data);
        FilmEffects.storeToFilm();
        this.updateFields();
    }

    private void reset()
    {
        this.pasteFilters(new MapType());
        UIUtils.playClick();
    }

}
