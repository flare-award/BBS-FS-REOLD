package mchorse.bbs_mod.graphics.texture;

import mchorse.bbs_mod.BBSModClient;
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
 * <p>Two things live here. The LabPBR side: the Material tab's five sliders are
 * flushed into this registry by the form renderers every frame, and when a shader
 * pack asks Iris for a texture's {@code _s}/{@code _n} companions, the loader
 * checks here first - a synthesized specular map carries the slider values
 * (smoothness, F0, subsurface scattering, emission), and a normal map embossed
 * from the texture's own luminance carries the relief. The color overlay side:
 * {@link #getOverlayed(Link, Texture, Color)} serves a copy of a texture with
 * every pixel mixed toward the overlay color, which recolors the form the same
 * way under any pipeline, shader packs included.</p>
 */
public class FormMaterials
{
    /* LabPBR specular green channel: 0..229 store F0 linearly, 230+ are metals */
    private static final float LAB_PBR_F0_MAX = 229F;
    private static final int LAB_PBR_METAL = 255;

    /* LabPBR specular blue channel: subsurface scattering occupies 65..255 */
    private static final int LAB_PBR_SSS_MIN = 65;

    /* How steep the relief slider's full swing tilts the embossed normals */
    private static final float RELIEF_NORMAL_STRENGTH = 4F;

    private static final int SPECULAR_SIZE = 16;

    private static final Map<Link, PBREntry> pbr = new HashMap<>();
    private static final Map<Link, OverlayEntry> overlays = new HashMap<>();

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
        float relief = form.relief.get();

        if (smoothness <= 0F && metalic <= 0F && sss <= 0F && emission <= 0F && relief <= 0F)
        {
            PBREntry entry = pbr.remove(texture);

            if (entry != null)
            {
                entry.delete();
            }

            return;
        }

        pbr.computeIfAbsent(texture, (key) -> new PBREntry()).set(smoothness, metalic, sss, emission, relief);
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
     * GL id of the relief normal map embossed from the texture's luminance, or
     * -1 when relief is off (the file-based {@code _n} companion applies then).
     */
    public static int getNormalId(Link texture)
    {
        PBREntry entry = pbr.get(texture);

        return entry == null ? -1 : entry.getNormalId(texture);
    }

    /**
     * The texture to actually bind for a form with a color overlay: a cached
     * copy whose pixels are mixed toward the overlay color by its alpha. With
     * no overlay (or a broken texture) the base texture comes back untouched.
     */
    public static Texture getOverlayed(Link link, Texture base, Color overlay)
    {
        if (link == null || base == null || overlay == null || overlay.a <= 0F || !base.isValid())
        {
            return base;
        }

        return overlays.computeIfAbsent(link, (key) -> new OverlayEntry()).get(link, base, overlay);
    }

    /** The five slider values and the GL textures they bake into. */
    private static class PBREntry
    {
        private float smoothness;
        private float metalic;
        private float sss;
        private float emission;
        private float relief;

        private boolean specularDirty = true;
        private boolean normalDirty = true;

        private Texture specular;
        private Texture normal;

        /* The base texture's cached luminance for the relief emboss */
        private float[] luminance;
        private int luminanceId = -1;
        private int luminanceWidth;
        private int luminanceHeight;

        public void set(float smoothness, float metalic, float sss, float emission, float relief)
        {
            if (this.smoothness != smoothness || this.metalic != metalic || this.sss != sss || this.emission != emission)
            {
                this.specularDirty = true;
            }

            if (this.relief != relief)
            {
                this.normalDirty = true;
            }

            this.smoothness = smoothness;
            this.metalic = metalic;
            this.sss = sss;
            this.emission = emission;
            this.relief = relief;
        }

        public int getSpecularId()
        {
            if (this.specularDirty)
            {
                this.uploadSpecular();
                this.specularDirty = false;
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

        public int getNormalId(Link texture)
        {
            if (this.relief <= 0F)
            {
                return -1;
            }

            Texture base = BBSModClient.getTextures().getTexture(texture);

            if (base == null || !base.isValid() || base == BBSModClient.getTextures().getError())
            {
                return -1;
            }

            if (this.luminance == null || this.luminanceId != base.id)
            {
                this.cacheLuminance(base);
                this.normalDirty = true;
            }

            if (this.normalDirty)
            {
                this.uploadNormal();
                this.normalDirty = false;
            }

            return this.normal == null ? -1 : this.normal.id;
        }

        private void cacheLuminance(Texture base)
        {
            Pixels pixels = Texture.pixelsFromTexture(base);

            if (pixels == null)
            {
                return;
            }

            ByteBuffer buffer = pixels.getBuffer();

            this.luminance = new float[pixels.getCount()];
            this.luminanceId = base.id;
            this.luminanceWidth = pixels.width;
            this.luminanceHeight = pixels.height;

            for (int i = 0; i < this.luminance.length; i++)
            {
                int r = buffer.get(i * 4) & 0xFF;
                int g = buffer.get(i * 4 + 1) & 0xFF;
                int b = buffer.get(i * 4 + 2) & 0xFF;

                this.luminance[i] = (0.2126F * r + 0.7152F * g + 0.0722F * b) / 255F;
            }

            pixels.delete();
        }

        /**
         * Emboss a LabPBR normal map out of the texture's luminance: bright
         * pixels sit at the surface, dark ones sink by up to the relief depth,
         * and the normals tilt along that height field's slope. That gives the
         * slider a real, visible effect - normal-mapped shading in any LabPBR
         * pack, parallax where the pack supports it.
         */
        private void uploadNormal()
        {
            if (this.luminance == null)
            {
                return;
            }

            int w = this.luminanceWidth;
            int h = this.luminanceHeight;
            float strength = this.relief * RELIEF_NORMAL_STRENGTH;
            Pixels pixels = Pixels.fromSize(w, h);
            Color color = new Color();

            for (int y = 0; y < h; y++)
            {
                for (int x = 0; x < w; x++)
                {
                    float left = this.luminance[y * w + Math.max(x - 1, 0)];
                    float right = this.luminance[y * w + Math.min(x + 1, w - 1)];
                    float up = this.luminance[Math.max(y - 1, 0) * w + x];
                    float down = this.luminance[Math.min(y + 1, h - 1) * w + x];
                    float dx = (right - left) * strength;
                    float dy = (down - up) * strength;
                    float invLength = 1F / (float) Math.sqrt(dx * dx + dy * dy + 1F);

                    float nx = -dx * invLength * 0.5F + 0.5F;
                    float ny = -dy * invLength * 0.5F + 0.5F;
                    float height = 1F - this.relief * (1F - this.luminance[y * w + x]);

                    color.set(nx, ny, 1F, MathUtils.clamp(height, 0F, 1F));
                    pixels.setColor(x, y, color);
                }
            }

            pixels.rewindBuffer();

            if (this.normal == null)
            {
                /* textureFromPixels frees the pixels itself */
                this.normal = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);
            }
            else
            {
                this.normal.bind();
                this.normal.updateTexture(pixels);
                this.normal.unbind();
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

            if (this.normal != null)
            {
                this.normal.delete();
                this.normal = null;
            }

            this.luminance = null;
            this.luminanceId = -1;
        }
    }

    /** A texture copy with the pixels mixed toward the overlay color. */
    private static class OverlayEntry
    {
        private Texture derived;
        private Pixels basePixels;
        private int baseId = -1;
        private int lastColor;

        public Texture get(Link link, Texture base, Color overlay)
        {
            int color = overlay.getARGBColor();

            if (this.basePixels == null || this.baseId != base.id)
            {
                if (this.basePixels != null)
                {
                    this.basePixels.delete();
                }

                this.basePixels = Texture.pixelsFromTexture(base);
                this.baseId = base.id;
                this.lastColor = 0;

                if (this.basePixels == null)
                {
                    return base;
                }
            }

            if (this.derived == null || this.lastColor != color)
            {
                this.upload(link, base, overlay);
                this.lastColor = color;
            }

            return this.derived == null ? base : this.derived;
        }

        private void upload(Link link, Texture base, Color overlay)
        {
            Pixels mixed = Pixels.fromSize(this.basePixels.width, this.basePixels.height);
            ByteBuffer src = this.basePixels.getBuffer();
            ByteBuffer dst = mixed.getBuffer();
            float a = MathUtils.clamp(overlay.a, 0F, 1F);
            float r = overlay.r * 255F;
            float g = overlay.g * 255F;
            float b = overlay.b * 255F;

            for (int i = 0, c = mixed.getCount() * 4; i < c; i += 4)
            {
                dst.put(i, (byte) ((int) ((src.get(i) & 0xFF) * (1F - a) + r * a)));
                dst.put(i + 1, (byte) ((int) ((src.get(i + 1) & 0xFF) * (1F - a) + g * a)));
                dst.put(i + 2, (byte) ((int) ((src.get(i + 2) & 0xFF) * (1F - a) + b * a)));
                dst.put(i + 3, src.get(i + 3));
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
                 * PBR companions (file-based or synthesized) for the recolored copy */
                IrisUtils.trackSynthetic(this.derived.id, link);
            }
        }
    }
}
