package mchorse.bbs_mod.graphics.texture;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-form material data baked into textures at render time.
 *
 * <p>Two things live here. The LabPBR side: four of the Material tab's sliders
 * (gloss, metallic, scattering, emission) are flushed into this registry by the
 * form renderers every frame, and when a shader pack asks Iris for a texture's
 * {@code _s} companion, the loader checks here first - a synthesized specular
 * map carries the slider values in LabPBR encoding. The processed-texture side:
 * {@link #getProcessed(Link, Texture, Form)} serves a copy of a texture with
 * the relief slider embossed into the pixels (bright texels ridge up, dark ones
 * sink - visible under any pipeline) and every pixel mixed toward the color
 * overlay, which recolors the form the same way with or without shader packs.</p>
 */
public class FormMaterials
{
    /* LabPBR specular green channel: 0..229 store F0 linearly, 230+ are metals */
    private static final float LAB_PBR_F0_MAX = 229F;
    private static final int LAB_PBR_METAL = 255;

    /* LabPBR specular blue channel: subsurface scattering occupies 65..255 */
    private static final int LAB_PBR_SSS_MIN = 65;

    /* How hard the relief slider's full swing carves the emboss into the pixels */
    private static final float RELIEF_STRENGTH = 220F;

    private static final int SPECULAR_SIZE = 16;

    private static final Map<Link, PBREntry> pbr = new HashMap<>();
    private static final Map<Link, ProcessedEntry> processed = new HashMap<>();

    /**
     * The form whose model is being drawn right now. Per-material texture
     * binds happen deep inside the model renderers, which don't know the
     * form - this hands it to them so multi-material models get the same
     * material treatment as single-texture ones.
     */
    private static Form currentForm;

    public static void setCurrentForm(Form form)
    {
        currentForm = form;
    }

    /**
     * Run a per-material texture through the current form's material pipeline:
     * feed the PBR sliders and serve the processed copy. Without a current
     * form (or anything to process) the texture passes through untouched.
     */
    public static Texture processCurrent(Link link, Texture texture)
    {
        if (currentForm == null || link == null || texture == null)
        {
            return texture;
        }

        update(link, currentForm);

        return getProcessed(link, texture, currentForm);
    }

    /**
     * Record the form's material sliders for its texture. Called by the form
     * renderers right before they bind, so by the time Iris resolves the
     * texture's PBR companions the values of the form drawing it are in.
     */
    public static void update(Link texture, Form form)
    {
        if (texture == null || form == null)
        {
            return;
        }

        float smoothness = form.smoothness.get();
        float metalic = form.metalic.get();
        float sss = form.sss.get();
        float emission = form.pixelEmission.get();

        if (smoothness <= 0F && metalic <= 0F && sss <= 0F && emission <= 0F)
        {
            PBREntry entry = pbr.remove(texture);

            if (entry != null)
            {
                entry.delete();
            }

            return;
        }

        pbr.computeIfAbsent(texture, (key) -> new PBREntry()).set(smoothness, metalic, sss, emission);
    }

    /**
     * GL id of the synthesized LabPBR specular map for this texture, or -1 when
     * the form drawing it has no material sliders set (the file-based
     * {@code _s} companion applies then).
     */
    public static int getSpecularId(Link texture)
    {
        PBREntry entry = pbr.get(texture);

        return entry == null ? -1 : entry.getSpecularId();
    }

    /**
     * The texture to actually bind for the form: a cached copy with the hue and
     * saturation adjusted, the relief emboss carved in and the pixels mixed
     * toward the color overlay. With every effect off (or a broken texture) the
     * base texture comes back untouched.
     */
    public static Texture getProcessed(Link link, Texture base, Form form)
    {
        if (link == null || base == null || form == null || !base.isValid())
        {
            return base;
        }

        Color overlay = form.colorOverlay.get();
        float relief = form.relief.get();
        float hue = form.hue.get();
        float saturation = form.saturation.get();

        if (overlay.a <= 0F && relief <= 0F && hue == 0F && saturation == 1F)
        {
            return base;
        }

        return processed.computeIfAbsent(link, (key) -> new ProcessedEntry()).get(link, base, overlay, relief, hue, saturation);
    }

    /** The four LabPBR slider values and the specular texture they bake into. */
    private static class PBREntry
    {
        private float smoothness;
        private float metalic;
        private float sss;
        private float emission;

        private boolean dirty = true;

        private Texture specular;

        public void set(float smoothness, float metalic, float sss, float emission)
        {
            if (this.smoothness != smoothness || this.metalic != metalic || this.sss != sss || this.emission != emission)
            {
                this.dirty = true;
            }

            this.smoothness = smoothness;
            this.metalic = metalic;
            this.sss = sss;
            this.emission = emission;
        }

        public int getSpecularId()
        {
            if (this.dirty)
            {
                this.uploadSpecular();
                this.dirty = false;
            }

            return this.specular == null ? -1 : this.specular.id;
        }

        /**
         * A single flat color is enough for the specular map: every LabPBR
         * channel here is one value across the whole form. The 16x16 size just
         * keeps packs that read neighboring texels out of trouble.
         */
        private void uploadSpecular()
        {
            Pixels pixels = Pixels.fromSize(SPECULAR_SIZE, SPECULAR_SIZE);
            int g = this.metalic >= 0.995F ? LAB_PBR_METAL : Math.round(this.metalic * LAB_PBR_F0_MAX);
            int b = this.sss <= 0F ? 0 : LAB_PBR_SSS_MIN + Math.round(this.sss * (255 - LAB_PBR_SSS_MIN));
            Color color = new Color(this.smoothness, g / 255F, b / 255F, this.emission * 254F / 255F);

            for (int i = 0, c = pixels.getCount(); i < c; i++)
            {
                pixels.setColor(i, color);
            }

            pixels.rewindBuffer();

            if (this.specular == null)
            {
                /* textureFromPixels frees the pixels itself */
                this.specular = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);
            }
            else
            {
                this.specular.bind();
                this.specular.updateTexture(pixels);
                this.specular.unbind();
                pixels.delete();
            }
        }

        public void delete()
        {
            if (this.specular != null)
            {
                this.specular.delete();
                this.specular = null;
            }
        }
    }

    /** A texture copy with the relief embossed in and the pixels mixed toward the overlay. */
    private static class ProcessedEntry
    {
        private Texture derived;
        private Pixels basePixels;
        private float[] luminance;
        private int baseId = -1;
        private int lastColor;
        private float lastRelief = -1F;
        private float lastHue;
        private float lastSaturation = 1F;

        public Texture get(Link link, Texture base, Color overlay, float relief, float hue, float saturation)
        {
            int color = overlay.a <= 0F ? 0 : overlay.getARGBColor();

            if (this.basePixels == null || this.baseId != base.id)
            {
                if (this.basePixels != null)
                {
                    this.basePixels.delete();
                }

                this.basePixels = Texture.pixelsFromTexture(base);
                this.baseId = base.id;
                this.lastColor = 0;
                this.lastRelief = -1F;
                this.lastHue = 0F;
                this.lastSaturation = 1F;
                this.luminance = null;

                if (this.basePixels == null)
                {
                    return base;
                }
            }

            if (this.derived == null || this.lastColor != color || this.lastRelief != relief || this.lastHue != hue || this.lastSaturation != saturation)
            {
                this.upload(link, base, overlay, relief, hue, saturation);
                this.lastColor = color;
                this.lastRelief = relief;
                this.lastHue = hue;
                this.lastSaturation = saturation;
            }

            return this.derived == null ? base : this.derived;
        }

        /** The base texture's luminance, for the relief emboss. */
        private void cacheLuminance()
        {
            ByteBuffer buffer = this.basePixels.getBuffer();

            this.luminance = new float[this.basePixels.getCount()];

            for (int i = 0; i < this.luminance.length; i++)
            {
                int r = buffer.get(i * 4) & 0xFF;
                int g = buffer.get(i * 4 + 1) & 0xFF;
                int b = buffer.get(i * 4 + 2) & 0xFF;

                this.luminance[i] = (0.2126F * r + 0.7152F * g + 0.0722F * b) / 255F;
            }
        }

        /**
         * Bake the effects into the derived copy, in order: hue rotation and
         * saturation first, then relief - a classic emboss where every pixel is
         * raised or sunk by the luminance slope of its diagonal neighbors, so
         * texture detail reads as carved ridges - and finally the color
         * overlay, which mixes every pixel toward its color, alpha being the
         * strength. All of it works under any pipeline, shader packs or not.
         */
        private void upload(Link link, Texture base, Color overlay, float relief, float hue, float saturation)
        {
            if (relief > 0F && this.luminance == null)
            {
                this.cacheLuminance();
            }

            Pixels mixed = Pixels.fromSize(this.basePixels.width, this.basePixels.height);
            ByteBuffer src = this.basePixels.getBuffer();
            ByteBuffer dst = mixed.getBuffer();
            int w = this.basePixels.width;
            int h = this.basePixels.height;
            float a = overlay.a <= 0F ? 0F : MathUtils.clamp(overlay.a, 0F, 1F);
            float or = overlay.r * 255F;
            float og = overlay.g * 255F;
            float ob = overlay.b * 255F;
            float carve = relief * RELIEF_STRENGTH;

            /* Hue rotation around the grayscale axis, same math as the film filter's hue shift */
            float angle = MathUtils.toRad(hue);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float k = 0.57735F;

            for (int y = 0; y < h; y++)
            {
                for (int x = 0; x < w; x++)
                {
                    int i = (y * w + x) * 4;
                    float r = src.get(i) & 0xFF;
                    float g = src.get(i + 1) & 0xFF;
                    float b = src.get(i + 2) & 0xFF;

                    if (hue != 0F)
                    {
                        float crossR = k * (b - g);
                        float crossG = k * (r - b);
                        float crossB = k * (g - r);
                        float dot = k * (r + g + b) * (1F - cos);

                        float newR = r * cos + crossR * sin + k * dot;
                        float newG = g * cos + crossG * sin + k * dot;
                        float newB = b * cos + crossB * sin + k * dot;

                        r = newR;
                        g = newG;
                        b = newB;
                    }

                    if (saturation != 1F)
                    {
                        float luma = 0.2126F * r + 0.7152F * g + 0.0722F * b;

                        r = luma + (r - luma) * saturation;
                        g = luma + (g - luma) * saturation;
                        b = luma + (b - luma) * saturation;
                    }

                    if (carve > 0F)
                    {
                        int i1 = Math.max(y - 1, 0) * w + Math.max(x - 1, 0);
                        int i2 = Math.min(y + 1, h - 1) * w + Math.min(x + 1, w - 1);
                        float delta = (this.luminance[i1] - this.luminance[i2]) * carve;

                        r += delta;
                        g += delta;
                        b += delta;
                    }

                    if (a > 0F)
                    {
                        r = r * (1F - a) + or * a;
                        g = g * (1F - a) + og * a;
                        b = b * (1F - a) + ob * a;
                    }

                    dst.put(i, (byte) (int) MathUtils.clamp(r, 0F, 255F));
                    dst.put(i + 1, (byte) (int) MathUtils.clamp(g, 0F, 255F));
                    dst.put(i + 2, (byte) (int) MathUtils.clamp(b, 0F, 255F));
                    dst.put(i + 3, src.get(i + 3));
                }
            }

            boolean fresh = this.derived == null;

            mixed.rewindBuffer();

            if (fresh)
            {
                /* textureFromPixels frees the pixels itself */
                this.derived = Texture.textureFromPixels(mixed, base.getFilter());
            }
            else
            {
                this.derived.bind();
                this.derived.updateTexture(mixed);
                this.derived.unbind();
                mixed.delete();
            }

            if (fresh && BBSRendering.isIrisShadersEnabled())
            {
                /* Registered under the base texture's link, so Iris resolves the same
                 * PBR companions (file-based or synthesized) for the processed copy */
                IrisUtils.trackSynthetic(this.derived.id, link);
            }
        }
    }
}
