package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.resources.LinkUtils;

/**
 * A billboard-shaped world lens. Its mask is an internal white pixel, so the
 * form remains a rectangular lens without exposing billboard texture settings.
 */
public class FilterBoardForm extends BillboardForm
{
    public static final Link MASK_TEXTURE = LinkUtils.color(1F, 1F, 1F);

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
    public final ValueFloat bloom = new ValueFloat("bloom", 0F, 0F, 1F);
    public final ValueFloat radial = new ValueFloat("radial", 0F, 0F, 1F);
    public final ValueFloat vhs = new ValueFloat("vhs", 0F, 0F, 1F);

    public FilterBoardForm()
    {
        super();

        /* Keep only the billboard orientation value; the lens size comes from
         * the standard transform and its internal mask is not user-selectable. */
        this.texture.set(MASK_TEXTURE);
        this.remove(this.texture);
        this.remove(this.linear);
        this.remove(this.mipmap);
        this.remove(this.crop);
        this.remove(this.resizeCrop);
        this.remove(this.color);
        this.remove(this.offsetX);
        this.remove(this.offsetY);
        this.remove(this.rotation);
        this.remove(this.shading);

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
        this.filters.add(this.bloom);
        this.filters.add(this.radial);
        this.filters.add(this.vhs);
        this.add(this.filters);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.getFormId();
    }
}
