package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * A billboard-shaped world lens. The billboard texture is used only as an
 * alpha mask; the renderer samples the already rendered scene underneath it
 * and applies these values to that sample.
 */
public class FilterBoardForm extends BillboardForm
{
    public final ValueGroup filters = new ValueGroup("filters");

    public final ValueFloat brightness = new ValueFloat("brightness", 0F, -1F, 1F);
    public final ValueFloat contrast = new ValueFloat("contrast", 0F, -1F, 1F);
    public final ValueFloat filterSaturation = new ValueFloat("saturation", 0F, -1F, 1F);
    public final ValueFloat filterHue = new ValueFloat("hue", 0F, -180F, 180F);
    public final ValueFloat temperature = new ValueFloat("temperature", 0F, -1F, 1F);
    public final ValueFloat gamma = new ValueFloat("gamma", 1F, BBSSettings.MIN_FILM_GAMMA, BBSSettings.MAX_FILM_GAMMA);
    public final ValueFloat sharpness = new ValueFloat("sharpness", 0F, 0F, 1F);
    public final ValueFloat vignette = new ValueFloat("vignette", 0F, -1F, 1F);
    public final ValueFloat sepia = new ValueFloat("sepia", 0F, 0F, 1F);
    public final ValueFloat grain = new ValueFloat("grain", 0F, 0F, 1F);
    public final ValueFloat aberration = new ValueFloat("aberration", 0F, 0F, 1F);
    public final ValueFloat invert = new ValueFloat("invert", 0F, 0F, 1F);
    public final ValueFloat posterize = new ValueFloat("posterize", 0F, 0F, BBSSettings.MAX_FILM_POSTERIZE);
    public final ValueFloat pixelate = new ValueFloat("pixelate", 0F, 0F, BBSSettings.MAX_FILM_PIXELATE);
    public final ValueFloat distortion = new ValueFloat("distortion", 0F, -1F, 1F);
    public final ValueFloat bloom = new ValueFloat("bloom", 0F, 0F, 1F);
    public final ValueFloat radial = new ValueFloat("radial", 0F, 0F, 1F);
    public final ValueFloat vhs = new ValueFloat("vhs", 0F, 0F, 1F);
    public final ValueFloat flip = new ValueFloat("flip", 0F, 0F, 2F);
    public final ValueFloat fisheye = new ValueFloat("fisheye", 0F, -1F, 1F);

    public FilterBoardForm()
    {
        super();

        this.filters.add(this.brightness);
        this.filters.add(this.contrast);
        this.filters.add(this.filterSaturation);
        this.filters.add(this.filterHue);
        this.filters.add(this.temperature);
        this.filters.add(this.gamma);
        this.filters.add(this.sharpness);
        this.filters.add(this.vignette);
        this.filters.add(this.sepia);
        this.filters.add(this.grain);
        this.filters.add(this.aberration);
        this.filters.add(this.invert);
        this.filters.add(this.posterize);
        this.filters.add(this.pixelate);
        this.filters.add(this.distortion);
        this.filters.add(this.bloom);
        this.filters.add(this.radial);
        this.filters.add(this.vhs);
        this.filters.add(this.flip);
        this.filters.add(this.fisheye);
        this.add(this.filters);
    }
}
