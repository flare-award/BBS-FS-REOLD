package mchorse.bbs_mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.FilterClip;
import mchorse.bbs_mod.camera.clips.misc.PhotoClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Post-processing baked into the film's export texture.
 *
 * <p>Right after {@link BBSRendering#onRenderBeforeScreen()} blits the frame into the
 * export framebuffer, this class color-grades it with a fullscreen shader pass and lays
 * photo layers over it. The film preview, the video recorder and the screenshot all read
 * that very texture, so whatever happens here shows up in every one of them at once.</p>
 *
 * <p>Slider values come from {@link BBSSettings}, but while a {@link FilterClip} plays
 * on the camera timeline, its keyframed channels override the matching sliders for
 * that frame - which is how the filters animate in playback and export alike.</p>
 */
public class FilmEffects
{
    /* Photo layer modes: over the whole frame, behind the film's actors,
     * behind world-placed model blocks, or behind both. */
    public static final int LAYER_OVER = 0;
    public static final int LAYER_BEHIND_ACTORS = 1;
    public static final int LAYER_BEHIND_BLOCKS = 2;
    public static final int LAYER_BEHIND_MODELS = 3;

    /** Below this distance from its neutral value a filter is considered off. */
    private static final float NEUTRAL_EPSILON = 0.0001F;

    /** How much the sharpness slider's full swing weighs in the unsharp mask. */
    private static final float SHARPNESS_STRENGTH = 2F;

    /** How far the temperature slider's full swing pushes the red/blue channels. */
    private static final float TEMPERATURE_STRENGTH = 0.2F;

    /** How much smaller than the frame the bloom buffers are (in each axis). */
    private static final int BLOOM_DOWNSCALE = 4;

    private static final String FILTER_VERTEX = """
        #version 150

        in vec2 a_position;

        out vec2 v_uv;

        void main()
        {
            v_uv = a_position * 0.5 + 0.5;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }""";

    private static final String FILTER_FRAGMENT = """
        #version 150

        uniform sampler2D u_texture;
        uniform sampler2D u_bloom;
        uniform vec2 u_texel;
        uniform float u_brightness;
        uniform float u_contrast;
        uniform float u_saturation;
        uniform float u_hue;
        uniform float u_temperature;
        uniform float u_gamma;
        uniform float u_sharpness;
        uniform float u_vignette;
        uniform float u_sepia;
        uniform float u_grain;
        uniform float u_aberration;
        uniform float u_invert;
        uniform float u_posterize;
        uniform float u_pixelate;
        uniform float u_distortion;
        uniform float u_bloom_strength;
        uniform float u_radial;
        uniform float u_vhs;
        uniform float u_flip;
        uniform float u_fisheye;
        uniform float u_seed;

        in vec2 v_uv;

        out vec4 fragColor;

        vec3 hueShift(vec3 color, float angle)
        {
            const vec3 k = vec3(0.57735);
            float c = cos(angle);

            return color * c + cross(k, color) * sin(angle) + k * dot(k, color) * (1.0 - c);
        }

        vec3 sampleFrame(vec2 uv)
        {
            if (u_aberration > 0.0)
            {
                /* Radial fringing: red samples outward, blue inward */
                vec2 shift = (uv - 0.5) * u_aberration * 0.03;

                return vec3(
                    texture(u_texture, uv + shift).r,
                    texture(u_texture, uv).g,
                    texture(u_texture, uv - shift).b);
            }

            return texture(u_texture, uv).rgb;
        }

        void main()
        {
            vec2 uv = v_uv;

            if (u_flip == 1.0)
            {
                uv.y = 1.0 - uv.y;
            }
            else if (u_flip == 2.0)
            {
                uv.x = 1.0 - uv.x;
            }

            if (u_distortion != 0.0)
            {
                /* Barrel (positive) or pincushion (negative) lens warp,
                 * aspect-corrected so the bulge stays circular */
                float aspect = u_texel.y / u_texel.x;
                vec2 d = uv - 0.5;

                d.x *= aspect;
                d *= 1.0 + u_distortion * dot(d, d) * 1.5;
                d.x /= aspect;
                uv = clamp(d + 0.5, 0.0, 1.0);
            }

            if (u_fisheye != 0.0)
            {
                /* Positive bulges the center out like a fisheye lens, negative pinches it in */
                float aspect = u_texel.y / u_texel.x;
                vec2 d = uv - 0.5;

                d.x *= aspect;

                float r = length(d) * 1.4142;

                if (r > 0.0001)
                {
                    d *= pow(r, 1.0 + u_fisheye * 0.75) / r;
                }

                d.x /= aspect;
                uv = clamp(d + 0.5, 0.0, 1.0);
            }

            if (u_pixelate >= 1.0)
            {
                /* Snap the sample point to the center of a u_pixelate-sized cell */
                vec2 cell = u_texel * u_pixelate;

                uv = (floor(uv / cell) + 0.5) * cell;
            }

            if (u_vhs > 0.0)
            {
                /* Per-scanline horizontal jitter, tape-style: a few sines at odd
                 * frequencies, seeded by the film clock so exports are stable */
                float line = floor(v_uv.y / u_texel.y);
                float wobble = sin(line * 0.35 + u_seed * 0.63) * sin(line * 0.043 + u_seed * 0.121);

                uv.x += wobble * u_texel.x * u_vhs * 4.0;
            }

            vec3 color = sampleFrame(uv);

            if (u_radial > 0.0)
            {
                /* Zoom blur: smear samples toward the frame's center */
                vec2 toCenter = (vec2(0.5) - uv) * u_radial * 0.12;
                vec3 accumulated = color;

                for (int i = 1; i < 8; i++)
                {
                    accumulated += sampleFrame(uv + toCenter * (float(i) / 8.0));
                }

                color = accumulated / 8.0;
            }

            if (u_vhs > 0.0)
            {
                /* Chroma bleed to the right and darkened scanlines */
                color.r = mix(color.r, sampleFrame(uv + vec2(u_texel.x * u_vhs * 2.0, 0.0)).r, 0.75);
                color.b = mix(color.b, sampleFrame(uv - vec2(u_texel.x * u_vhs * 2.0, 0.0)).b, 0.75);
                color *= 1.0 - u_vhs * 0.2 * (0.5 + 0.5 * sin(v_uv.y / u_texel.y * 3.14159));
            }

            if (u_sharpness > 0.0)
            {
                vec3 blur = sampleFrame(uv + vec2(u_texel.x, 0.0));

                blur += sampleFrame(uv - vec2(u_texel.x, 0.0));
                blur += sampleFrame(uv + vec2(0.0, u_texel.y));
                blur += sampleFrame(uv - vec2(0.0, u_texel.y));
                color += (color - blur * 0.25) * u_sharpness;
            }

            color *= u_brightness;
            color = (color - 0.5) * u_contrast + 0.5;
            color += vec3(u_temperature, 0.0, -u_temperature);

            float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

            color = mix(vec3(luma), color, u_saturation);
            color = hueShift(color, u_hue);
            color = pow(max(color, vec3(0.0)), vec3(u_gamma));
            color = mix(color, vec3(1.0) - color, u_invert);

            if (u_posterize >= 2.0)
            {
                /* u_posterize is the number of tones each channel keeps */
                float steps = u_posterize - 1.0;

                color = floor(clamp(color, 0.0, 1.0) * steps + 0.5) / steps;
            }

            vec3 sepia = vec3(
                dot(color, vec3(0.393, 0.769, 0.189)),
                dot(color, vec3(0.349, 0.686, 0.168)),
                dot(color, vec3(0.272, 0.534, 0.131)));

            color = mix(color, sepia, u_sepia);

            if (u_bloom_strength > 0.0)
            {
                color += texture(u_bloom, uv).rgb * u_bloom_strength;
            }

            if (u_vignette != 0.0)
            {
                /* Negative darkens the corners, positive washes them out white */
                float dist = distance(v_uv, vec2(0.5)) * 1.4142;
                float mask = abs(u_vignette) * smoothstep(0.35, 1.1, dist);

                color = mix(color, u_vignette > 0.0 ? vec3(1.0) : vec3(0.0), mask);
            }

            if (u_grain > 0.0)
            {
                /* Animated monochrome noise, one random value per output pixel */
                vec2 pixel = floor(v_uv / u_texel) + u_seed;
                float noise = fract(sin(dot(pixel, vec2(12.9898, 78.233))) * 43758.5453);

                color += (noise - 0.5) * u_grain * 0.3;
            }

            fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }""";

    private static final String BLOOM_CUT_FRAGMENT = """
        #version 150

        uniform sampler2D u_texture;
        uniform vec2 u_texel;

        in vec2 v_uv;

        out vec4 fragColor;

        void main()
        {
            /* Box down-sample so the quarter-res buffer doesn't shimmer */
            vec3 color = texture(u_texture, v_uv).rgb;

            color += texture(u_texture, v_uv + vec2(u_texel.x, 0.0)).rgb;
            color += texture(u_texture, v_uv + vec2(0.0, u_texel.y)).rgb;
            color += texture(u_texture, v_uv + u_texel).rgb;
            color *= 0.25;

            /* Keep only the highlights; the soft knee avoids hard flicker edges */
            float brightness = max(color.r, max(color.g, color.b));

            fragColor = vec4(color * smoothstep(0.6, 0.9, brightness), 1.0);
        }""";

    private static final String BLOOM_BLUR_FRAGMENT = """
        #version 150

        uniform sampler2D u_texture;
        uniform vec2 u_direction;

        in vec2 v_uv;

        out vec4 fragColor;

        void main()
        {
            float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
            vec3 color = texture(u_texture, v_uv).rgb * weights[0];

            for (int i = 1; i < 5; i++)
            {
                vec2 offset = u_direction * float(i);

                color += texture(u_texture, v_uv + offset).rgb * weights[i];
                color += texture(u_texture, v_uv - offset).rgb * weights[i];
            }

            fragColor = vec4(color, 1.0);
        }""";

    private static final String PHOTO_VERTEX = """
        #version 150

        uniform vec4 u_transform;
        uniform vec2 u_rotation;
        uniform float u_aspect;
        uniform float u_flip;

        in vec2 a_position;

        out vec2 v_uv;

        void main()
        {
            vec2 p = a_position * u_transform.zw;

            /* Rotate in frame space so the photo doesn't skew on wide screens */
            p.x *= u_aspect;
            p = vec2(p.x * u_rotation.x - p.y * u_rotation.y, p.x * u_rotation.y + p.y * u_rotation.x);
            p.x /= u_aspect;

            v_uv = vec2(a_position.x * 0.5 + 0.5, 0.5 - a_position.y * 0.5);

            if (u_flip == 1.0)
            {
                v_uv.y = 1.0 - v_uv.y;
            }
            else if (u_flip == 2.0)
            {
                v_uv.x = 1.0 - v_uv.x;
            }

            gl_Position = vec4(u_transform.xy + p, 0.0, 1.0);
        }""";

    private static final String PHOTO_FRAGMENT = """
        #version 150

        uniform sampler2D u_texture;
        uniform float u_opacity;

        in vec2 v_uv;

        out vec4 fragColor;

        void main()
        {
            vec4 color = texture(u_texture, v_uv);

            fragColor = vec4(color.rgb, color.a * u_opacity);
        }""";

    /* Once any GL setup fails, the effects stay off instead of failing every frame */
    private static boolean broken;

    /* While held down by the filters overlay's compare button, filters are skipped */
    private static boolean showOriginal;

    /* While held down by the photo overlay's compare button, photo layers are skipped */
    private static boolean showNoPhoto;

    /* Up while this class itself replays model blocks, so the dispatcher hide-out steps aside */
    private static boolean preRenderingModelBlocks;

    /* Whether model blocks were already replayed this frame (for the in-world photo modes) */
    private static boolean modelBlocksReplayed;

    /* A world-mode photo was drawn this frame: everything the world draws after it
     * gets fenced off with a near-plane depth stamp, so water, mobs and particles
     * can't land on top of the photo. */
    private static boolean stampPending;

    /* The film whose effects are currently loaded into the working sliders */
    private static Film currentFilm;

    private static int vao;
    private static int vbo;
    private static int filterProgram;
    private static int photoProgram;
    private static int bloomCutProgram;
    private static int bloomBlurProgram;
    private static int bloomFramebuffer;
    private static Texture pingTexture;
    private static Texture bloomTextureA;
    private static Texture bloomTextureB;

    private static int uniformTexel;
    private static int uniformBrightness;
    private static int uniformContrast;
    private static int uniformSaturation;
    private static int uniformHue;
    private static int uniformTemperature;
    private static int uniformGamma;
    private static int uniformSharpness;
    private static int uniformVignette;
    private static int uniformSepia;
    private static int uniformGrain;
    private static int uniformAberration;
    private static int uniformInvert;
    private static int uniformPosterize;
    private static int uniformPixelate;
    private static int uniformDistortion;
    private static int uniformBloomStrength;
    private static int uniformRadial;
    private static int uniformVhs;
    private static int uniformFlip;
    private static int uniformFisheye;
    private static int uniformSeed;
    private static int uniformBloomCutTexel;
    private static int uniformBlurDirection;
    private static int uniformTransform;
    private static int uniformRotation;
    private static int uniformAspect;
    private static int uniformOpacity;
    private static int uniformPhotoFlip;

    /* The photo layer list is reparsed only when the serialized setting changes */
    private static String cachedLayersString;
    private static List<PhotoLayer> cachedLayers = new ArrayList<>();

    /**
     * Load the given film's effects into the working sliders, putting the
     * previous film's state away first. Filters and photo layers are per-film:
     * they live in the film's data and follow it around.
     */
    public static void setCurrentFilm(Film film)
    {
        if (currentFilm == film)
        {
            return;
        }

        storeToFilm();
        currentFilm = film;
        loadFromFilm();
    }

    /**
     * Whether the film effects should show at all right now: only while the
     * film panel previews its film, or while a film camera plays or records.
     * Anywhere else (other dashboard panels, plain gameplay) the frame stays
     * untouched.
     */
    public static boolean isEffectsActive()
    {
        if (currentFilm == null)
        {
            return false;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController)
        {
            return true;
        }

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        return menu instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel;
    }

    /** Push the working sliders and photo layers into the current film's data. */
    public static void storeToFilm()
    {
        if (currentFilm == null || BBSSettings.filmFilterBrightness == null)
        {
            return;
        }

        MapType data = new MapType();

        data.put("filters", serializeFilters());
        data.putString("photo_layers", BBSSettings.filmPhotoLayers.get());
        currentFilm.effects.set(DataToString.toString(data));
    }

    private static void loadFromFilm()
    {
        MapType data = currentFilm == null ? null : DataToString.mapFromString(currentFilm.effects.get());

        applyFilterData(data == null ? new MapType() : data.getMap("filters"));
        BBSSettings.filmPhotoLayers.set(data == null ? "" : data.getString("photo_layers", ""));
    }

    /** The film filter sliders as one map - the canonical format for presets and film data alike. */
    public static MapType serializeFilters()
    {
        MapType data = new MapType();

        data.putFloat("brightness", BBSSettings.filmFilterBrightness.get());
        data.putFloat("contrast", BBSSettings.filmFilterContrast.get());
        data.putFloat("saturation", BBSSettings.filmFilterSaturation.get());
        data.putFloat("hue", BBSSettings.filmFilterHue.get());
        data.putFloat("temperature", BBSSettings.filmFilterTemperature.get());
        data.putFloat("gamma", BBSSettings.filmFilterGamma.get());
        data.putFloat("sharpness", BBSSettings.filmFilterSharpness.get());
        data.putFloat("vignette", BBSSettings.filmFilterVignette.get());
        data.putFloat("sepia", BBSSettings.filmFilterSepia.get());
        data.putFloat("grain", BBSSettings.filmFilterGrain.get());
        data.putFloat("aberration", BBSSettings.filmFilterAberration.get());
        data.putFloat("invert", BBSSettings.filmFilterInvert.get());
        data.putFloat("posterize", BBSSettings.filmFilterPosterize.get());
        data.putFloat("pixelate", BBSSettings.filmFilterPixelate.get());
        data.putFloat("distortion", BBSSettings.filmFilterDistortion.get());
        data.putFloat("bloom", BBSSettings.filmFilterBloom.get());
        data.putFloat("radial", BBSSettings.filmFilterRadial.get());
        data.putFloat("vhs", BBSSettings.filmFilterVhs.get());
        data.putFloat("flip", BBSSettings.filmFilterFlip.get());
        data.putFloat("fisheye", BBSSettings.filmFilterFisheye.get());

        return data;
    }

    /** Settings clamp on set, so hand-edited data can't push values out of range. */
    public static void applyFilterData(MapType data)
    {
        if (data == null)
        {
            data = new MapType();
        }

        BBSSettings.filmFilterBrightness.set(data.getFloat("brightness", 0F));
        BBSSettings.filmFilterContrast.set(data.getFloat("contrast", 0F));
        BBSSettings.filmFilterSaturation.set(data.getFloat("saturation", 0F));
        BBSSettings.filmFilterHue.set(data.getFloat("hue", 0F));
        BBSSettings.filmFilterTemperature.set(data.getFloat("temperature", 0F));
        BBSSettings.filmFilterGamma.set(data.getFloat("gamma", 1F));
        BBSSettings.filmFilterSharpness.set(data.getFloat("sharpness", 0F));
        BBSSettings.filmFilterVignette.set(data.getFloat("vignette", 0F));
        BBSSettings.filmFilterSepia.set(data.getFloat("sepia", 0F));
        BBSSettings.filmFilterGrain.set(data.getFloat("grain", 0F));
        BBSSettings.filmFilterAberration.set(data.getFloat("aberration", 0F));
        BBSSettings.filmFilterInvert.set(data.getFloat("invert", 0F));
        BBSSettings.filmFilterPosterize.set(data.getFloat("posterize", 0F));
        BBSSettings.filmFilterPixelate.set(data.getFloat("pixelate", 0F));
        BBSSettings.filmFilterDistortion.set(data.getFloat("distortion", 0F));
        BBSSettings.filmFilterBloom.set(data.getFloat("bloom", 0F));
        BBSSettings.filmFilterRadial.set(data.getFloat("radial", 0F));
        BBSSettings.filmFilterVhs.set(data.getFloat("vhs", 0F));
        BBSSettings.filmFilterFlip.set(data.getFloat("flip", 0F));
        BBSSettings.filmFilterFisheye.set(data.getFloat("fisheye", 0F));
    }

    /**
     * Whether the compare button in the filters overlay is held down right now,
     * which shows the frame with every filter bypassed.
     */
    public static boolean isShowingOriginal()
    {
        return showOriginal;
    }

    public static void setShowOriginal(boolean original)
    {
        showOriginal = original;
    }

    /**
     * Whether the compare button in the photo overlay is held down right now,
     * which shows the frame without any photo layers (filters stay applied).
     */
    public static boolean isShowingNoPhoto()
    {
        return showNoPhoto;
    }

    public static void setShowNoPhoto(boolean noPhoto)
    {
        showNoPhoto = noPhoto;
    }

    public static boolean hasFilters()
    {
        return hasFilters(getFilterState());
    }

    private static boolean hasFilters(FilterState state)
    {
        return isActive(state.brightness, 0F)
            || isActive(state.contrast, 0F)
            || isActive(state.saturation, 0F)
            || isActive(state.hue, 0F)
            || isActive(state.temperature, 0F)
            || isActive(state.gamma, 1F)
            || isActive(state.sharpness, 0F)
            || isActive(state.vignette, 0F)
            || isActive(state.sepia, 0F)
            || isActive(state.grain, 0F)
            || isActive(state.aberration, 0F)
            || isActive(state.invert, 0F)
            || state.posterize >= 2F
            || state.pixelate >= 1F
            || isActive(state.distortion, 0F)
            || isActive(state.bloom, 0F)
            || isActive(state.radial, 0F)
            || isActive(state.vhs, 0F)
            || state.flip >= 1F
            || isActive(state.fisheye, 0F);
    }

    public static boolean hasPhoto()
    {
        for (PhotoLayer layer : getPhotoLayers())
        {
            if (!layer.texture.isEmpty())
            {
                return true;
            }
        }

        for (PhotoClip.State state : getClipPhotoStates())
        {
            if (!state.texture.isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The current photo layer stack. The returned list is the live cache: the photo
     * overlay UI mutates its layers in place and persists them back through
     * {@link #savePhotoLayers(List)}.
     */
    public static List<PhotoLayer> getPhotoLayers()
    {
        if (BBSSettings.filmPhotoLayers == null)
        {
            return cachedLayers;
        }

        migrateLegacyPhoto();

        String serialized = BBSSettings.filmPhotoLayers.get();

        if (!serialized.equals(cachedLayersString))
        {
            cachedLayers = PhotoLayer.parseList(serialized);
            cachedLayersString = serialized;
        }

        return cachedLayers;
    }

    public static void savePhotoLayers(List<PhotoLayer> layers)
    {
        String serialized = PhotoLayer.serializeList(layers);

        cachedLayers = layers;
        cachedLayersString = serialized;
        BBSSettings.filmPhotoLayers.set(serialized);
        storeToFilm();
    }

    /**
     * The old single-photo settings fold into the first layer of the list the
     * first time the stack is read, so nobody's setup disappears on update.
     */
    private static void migrateLegacyPhoto()
    {
        String texture = BBSSettings.filmPhotoTexture == null ? "" : BBSSettings.filmPhotoTexture.get();

        if (texture == null || texture.isEmpty())
        {
            return;
        }

        List<PhotoLayer> layers = PhotoLayer.parseList(BBSSettings.filmPhotoLayers.get());
        PhotoLayer layer = new PhotoLayer();

        layer.texture = texture;
        layer.opacity = BBSSettings.filmPhotoOpacity.get();
        layer.x = BBSSettings.filmPhotoX.get();
        layer.y = BBSSettings.filmPhotoY.get();
        layer.scale = BBSSettings.filmPhotoScale.get();
        layer.stretchX = BBSSettings.filmPhotoStretchX.get();
        layer.stretchY = BBSSettings.filmPhotoStretchY.get();
        layers.add(layer);

        BBSSettings.filmPhotoLayers.set(PhotoLayer.serializeList(layers));
        BBSSettings.filmPhotoTexture.set("");
        cachedLayersString = null;
    }

    /**
     * The layer's photo loaded with linear filtering (a stretched photo shouldn't
     * turn blocky), or {@code null} when the layer has no texture picked.
     */
    public static Texture getPhotoTexture(PhotoLayer layer)
    {
        return getPhotoTexture(layer.texture);
    }

    public static Texture getPhotoTexture(String texture)
    {
        if (texture.isEmpty())
        {
            return null;
        }

        return BBSModClient.getTextures().getTexture(Link.create(texture), GL11.GL_LINEAR);
    }

    private static boolean isActive(float value, float neutral)
    {
        return Math.abs(value - neutral) > NEUTRAL_EPSILON;
    }

    /**
     * Bake the active effects into the export framebuffer's texture. GL state this
     * touches is saved up front and restored in {@code finally}, so vanilla's state
     * cache never goes stale no matter how the passes end.
     */
    public static void apply(Framebuffer framebuffer, int width, int height)
    {
        if (broken || !isEffectsActive() || width <= 0 || height <= 0)
        {
            return;
        }

        FilterState state = getFilterState();
        boolean filters = hasFilters(state) && !showOriginal;
        boolean photo = hasPostPhoto() && !showNoPhoto;

        if (!filters && !photo)
        {
            return;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);

        int prevTexture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        try
        {
            ensureGeometry();

            GL30.glBindVertexArray(vao);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.id);
            GL11.glViewport(0, 0, width, height);

            if (filters)
            {
                applyFilters(framebuffer, width, height, state);
            }

            if (photo)
            {
                applyPhotos(width, height);
            }
        }
        catch (Exception e)
        {
            broken = true;

            e.printStackTrace();
        }
        finally
        {
            RenderSystem.disableBlend();

            GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
            GL20.glUseProgram(prevProgram);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            GL13.glActiveTexture(prevActiveTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    /**
     * Color grading pass. A shader can't read the texture it writes to, so the frame
     * is snapshotted into a ping texture first and drawn back through the filters.
     * When bloom is on, the snapshot's highlights get blurred into a quarter-res
     * buffer beforehand, which the main pass adds back on top.
     */
    private static void applyFilters(Framebuffer framebuffer, int width, int height, FilterState state)
    {
        ensureFilterProgram();
        ensurePingTexture(width, height);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.id);
        pingTexture.bind();
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

        RenderSystem.disableBlend();

        boolean bloom = isActive(state.bloom, 0F);

        if (bloom)
        {
            applyBloom(framebuffer, width, height);
        }

        GL20.glUseProgram(filterProgram);
        GL20.glUniform2f(uniformTexel, 1F / width, 1F / height);
        GL20.glUniform1f(uniformBrightness, 1F + state.brightness);
        GL20.glUniform1f(uniformContrast, 1F + state.contrast);
        GL20.glUniform1f(uniformSaturation, 1F + state.saturation);
        GL20.glUniform1f(uniformHue, MathUtils.toRad(state.hue));
        GL20.glUniform1f(uniformTemperature, state.temperature * TEMPERATURE_STRENGTH);
        GL20.glUniform1f(uniformGamma, 1F / state.gamma);
        GL20.glUniform1f(uniformSharpness, state.sharpness * SHARPNESS_STRENGTH);
        GL20.glUniform1f(uniformVignette, state.vignette);
        GL20.glUniform1f(uniformSepia, state.sepia);
        GL20.glUniform1f(uniformGrain, state.grain);
        GL20.glUniform1f(uniformAberration, state.aberration);
        GL20.glUniform1f(uniformInvert, state.invert);
        GL20.glUniform1f(uniformPosterize, (float) Math.floor(state.posterize));
        GL20.glUniform1f(uniformPixelate, (float) Math.floor(state.pixelate));
        GL20.glUniform1f(uniformDistortion, state.distortion);
        GL20.glUniform1f(uniformBloomStrength, bloom ? state.bloom : 0F);
        GL20.glUniform1f(uniformRadial, state.radial);
        GL20.glUniform1f(uniformVhs, state.vhs);
        GL20.glUniform1f(uniformFlip, Math.round(state.flip));
        GL20.glUniform1f(uniformFisheye, state.fisheye);
        GL20.glUniform1f(uniformSeed, getGrainSeed());

        if (bloom)
        {
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            bloomTextureA.bind();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }

        pingTexture.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }

    /**
     * Fills {@link #bloomTextureA} with the frame's blurred highlights: threshold
     * cut at quarter resolution, then one horizontal and one vertical gaussian pass.
     */
    private static void applyBloom(Framebuffer framebuffer, int width, int height)
    {
        int bloomWidth = Math.max(1, width / BLOOM_DOWNSCALE);
        int bloomHeight = Math.max(1, height / BLOOM_DOWNSCALE);

        ensureBloomResources(bloomWidth, bloomHeight);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bloomFramebuffer);
        GL11.glViewport(0, 0, bloomWidth, bloomHeight);

        /* Threshold cut: frame -> A */
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, bloomTextureA.id, 0);
        GL20.glUseProgram(bloomCutProgram);
        GL20.glUniform2f(uniformBloomCutTexel, 1F / width, 1F / height);
        pingTexture.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        /* Horizontal blur: A -> B */
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, bloomTextureB.id, 0);
        GL20.glUseProgram(bloomBlurProgram);
        GL20.glUniform2f(uniformBlurDirection, 1F / bloomWidth, 0F);
        bloomTextureA.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        /* Vertical blur: B -> A */
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, bloomTextureA.id, 0);
        GL20.glUniform2f(uniformBlurDirection, 0F, 1F / bloomHeight);
        bloomTextureB.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.id);
        GL11.glViewport(0, 0, width, height);
    }

    private static int layerMode(PhotoLayer layer)
    {
        return clampLayerMode(layer.layerMode);
    }

    private static int clampLayerMode(float mode)
    {
        return Math.max(0, Math.min(3, Math.round(mode)));
    }

    /** Whether any layer or clip photo sits in the given layer mode. */
    private static boolean hasWorldPhoto(int mode)
    {
        for (PhotoLayer layer : getPhotoLayers())
        {
            if (!layer.texture.isEmpty() && layerMode(layer) == mode)
            {
                return true;
            }
        }

        for (PhotoClip.State state : getClipPhotoStates())
        {
            if (!state.texture.isEmpty() && clampLayerMode(state.layerMode) == mode)
            {
                return true;
            }
        }

        return false;
    }

    /** Whether the post pass has any photos left to draw: the over-frame layers and clip layers. */
    private static boolean hasPostPhoto()
    {
        for (PhotoLayer layer : getPhotoLayers())
        {
            if (!layer.texture.isEmpty() && layerMode(layer) == LAYER_OVER)
            {
                return true;
            }
        }

        for (PhotoClip.State state : getClipPhotoStates())
        {
            if (!state.texture.isEmpty() && clampLayerMode(state.layerMode) == LAYER_OVER)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the block entity dispatcher should skip model blocks this frame:
     * any in-world photo mode replays them itself at the ordering point the
     * mode calls for, so the regular draw would put them in the wrong place.
     * Never in the shadow pass - the blocks must keep casting shadows.
     */
    public static boolean shouldHideModelBlocks()
    {
        return !preRenderingModelBlocks && !broken && !showNoPhoto
            && !BBSRendering.isIrisShadowPass() && isEffectsActive()
            && (hasWorldPhoto(LAYER_BEHIND_ACTORS) || hasWorldPhoto(LAYER_BEHIND_BLOCKS) || hasWorldPhoto(LAYER_BEHIND_MODELS));
    }

    /**
     * Draw the in-world photo layers, so everything rendered after them covers
     * the photos while everything before stays behind. Runs twice around the
     * film's forms. Model blocks are replayed at the point each mode calls for
     * (their regular draw is skipped), and once any in-world photo is down, a
     * near-plane depth stamp fences the rest of the world pass off - water,
     * mobs, particles and the like can't land on top of the photo.
     *
     * <p>Photos draw through the vanilla textured program, which Iris redirects
     * into the shader pack's own pipeline - they survive deferred packs.</p>
     */
    public static void renderPhotosInWorld(WorldRenderContext context, boolean afterForms)
    {
        if (broken || showNoPhoto || !isEffectsActive())
        {
            return;
        }

        if (!afterForms)
        {
            modelBlocksReplayed = false;
            stampPending = false;

            /* Behind-the-actors photos want the blocks under themselves */
            if (hasWorldPhoto(LAYER_BEHIND_ACTORS))
            {
                renderModelBlocksEarly(context);
                modelBlocksReplayed = true;
            }
        }

        boolean draws = afterForms
            ? hasWorldPhoto(LAYER_BEHIND_BLOCKS)
            : hasWorldPhoto(LAYER_BEHIND_ACTORS) || hasWorldPhoto(LAYER_BEHIND_MODELS);

        if (draws)
        {
            drawWorldPhotos(afterForms);
            stampPending = true;
        }

        if (afterForms)
        {
            /* Behind-the-blocks and behind-the-models photos want the blocks above
             * themselves: replay them now, right after those photos went down */
            if (!modelBlocksReplayed && (hasWorldPhoto(LAYER_BEHIND_BLOCKS) || hasWorldPhoto(LAYER_BEHIND_MODELS)))
            {
                renderModelBlocksEarly(context);
                modelBlocksReplayed = true;
            }

            /* With shaders every form draws immediately, so the fence can go down
             * right here - it also blocks the entities Iris renders later. Without
             * shaders the deferred translucent form parts still have to land on
             * top, so the stamp waits for the translucent layer hook. */
            if (stampPending && BBSRendering.isIrisShadersEnabled())
            {
                stampPhotoDepth();
            }
        }
    }

    /** Stamp the near-plane depth fence if an in-world photo went down this frame. */
    public static void stampPhotoDepthIfPending()
    {
        if (stampPending)
        {
            stampPhotoDepth();
        }
    }

    /** The in-world photos of one stage: layers first, then the clip-driven ones. */
    private static void drawWorldPhotos(boolean afterForms)
    {
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        int width = viewport[2];
        int height = viewport[3];

        if (width <= 0 || height <= 0)
        {
            return;
        }

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter previousSorter = RenderSystem.getVertexSorting();
        MatrixStack modelViewStack = RenderSystem.getModelViewStack();

        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorter.BY_Z);
        modelViewStack.push();
        modelViewStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        try
        {
            for (PhotoLayer layer : getPhotoLayers())
            {
                if (drawsInStage(layerMode(layer), afterForms))
                {
                    drawPhotoInWorld(getPhotoTexture(layer.texture), layer.opacity, layer.x, layer.y, layer.scale, layer.stretchX, layer.stretchY, layer.rotate, layer.flip, width, height);
                }
            }

            for (PhotoClip.State state : getClipPhotoStates())
            {
                if (drawsInStage(clampLayerMode(state.layerMode), afterForms))
                {
                    drawPhotoInWorld(getPhotoTexture(state.texture), state.opacity, state.x, state.y, state.scale, state.stretchX, state.stretchY, state.rotate, state.flip, width, height);
                }
            }
        }
        catch (Exception e)
        {
            broken = true;

            e.printStackTrace();
        }
        finally
        {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            modelViewStack.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorter);
        }
    }

    private static boolean drawsInStage(int mode, boolean afterForms)
    {
        return afterForms
            ? mode == LAYER_BEHIND_BLOCKS
            : mode == LAYER_BEHIND_ACTORS || mode == LAYER_BEHIND_MODELS;
    }

    /**
     * A depth-only fullscreen quad at the near plane. Everything the world pass
     * draws after it fails the depth test where the photo is - water, mobs,
     * vanilla block entities and particles stay behind the photo, exactly like
     * the sky and the terrain that were drawn before it.
     */
    private static void stampPhotoDepth()
    {
        stampPending = false;

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter previousSorter = RenderSystem.getVertexSorting();
        MatrixStack modelViewStack = RenderSystem.getModelViewStack();
        Matrix4f identity = new Matrix4f();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setProjectionMatrix(identity, VertexSorter.BY_Z);
        modelViewStack.push();
        modelViewStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionProgram);

        try
        {
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            builder.vertex(identity, -1F, 1F, -1F).next();
            builder.vertex(identity, -1F, -1F, -1F).next();
            builder.vertex(identity, 1F, -1F, -1F).next();
            builder.vertex(identity, 1F, 1F, -1F).next();
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
        catch (Exception e)
        {
            broken = true;

            e.printStackTrace();
        }
        finally
        {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            modelViewStack.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorter);
        }
    }

    /**
     * Replay every ticking model block through the regular dispatcher at the
     * ordering point the photo modes call for; the dispatcher's own pass skips
     * them then (see {@link #shouldHideModelBlocks()}).
     */
    private static void renderModelBlocksEarly(WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        preRenderingModelBlocks = true;

        try
        {
            for (ModelBlockEntity entity : new ArrayList<>(BBSRendering.capturedModelBlocks))
            {
                if (entity.isRemoved() || entity.getWorld() != mc.world)
                {
                    continue;
                }

                BlockPos pos = entity.getPos();

                matrices.push();
                matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
                mc.getBlockEntityRenderDispatcher().render(entity, context.tickDelta(), matrices, immediate);
                matrices.pop();
            }

            immediate.draw();
        }
        catch (Exception e)
        {
            broken = true;

            e.printStackTrace();
        }
        finally
        {
            preRenderingModelBlocks = false;
        }
    }

    /** One photo quad in NDC, drawn through the vanilla textured program. */
    private static void drawPhotoInWorld(Texture photo, float opacity, float x, float y, float scale, float stretchX, float stretchY, float rotate, float flip, int width, int height)
    {
        if (photo == null || photo.width <= 0 || photo.height <= 0 || opacity <= 0F)
        {
            return;
        }

        float halfW = scale * stretchX * (photo.width / (float) photo.height) * (height / (float) width);
        float halfH = scale * stretchY;
        float angle = MathUtils.toRad(-rotate);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float aspect = width / (float) height;
        int flipMode = Math.round(flip);
        Matrix4f identity = new Matrix4f();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        BBSModClient.getTextures().bindTexture(photo);
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        /* Counterclockwise from the top left corner */
        float[] corners = {-1F, 1F, -1F, -1F, 1F, -1F, 1F, 1F};

        for (int i = 0; i < 4; i++)
        {
            float cx = corners[i * 2];
            float cy = corners[i * 2 + 1];

            /* Rotate in frame space so the photo doesn't skew on wide screens */
            float px = cx * halfW * aspect;
            float py = cy * halfH;
            float rx = (px * cos - py * sin) / aspect;
            float ry = px * sin + py * cos;

            float u = cx * 0.5F + 0.5F;
            float v = 0.5F - cy * 0.5F;

            if (flipMode == 1)
            {
                v = 1F - v;
            }
            else if (flipMode == 2)
            {
                u = 1F - u;
            }

            builder.vertex(identity, x + rx, -y + ry, 0F).texture(u, v).color(1F, 1F, 1F, opacity).next();
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /**
     * Photo pass, alpha-blended over the graded frame layer by layer. Each quad is
     * placed in NDC: scale 1 spans the frame's full height, the width keeps the
     * photo's aspect ratio, and the stretches multiply each axis on top of that.
     */
    private static void applyPhotos(int width, int height)
    {
        ensurePhotoProgram();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GL20.glUseProgram(photoProgram);

        for (PhotoLayer layer : getPhotoLayers())
        {
            if (layerMode(layer) == LAYER_OVER)
            {
                drawPhoto(getPhotoTexture(layer.texture), layer.opacity, layer.x, layer.y, layer.scale, layer.stretchX, layer.stretchY, layer.rotate, layer.flip, width, height);
            }
        }

        /* Layers animated by playing photo clips draw on top of the static stack */
        for (PhotoClip.State state : getClipPhotoStates())
        {
            if (clampLayerMode(state.layerMode) == LAYER_OVER)
            {
                drawPhoto(getPhotoTexture(state.texture), state.opacity, state.x, state.y, state.scale, state.stretchX, state.stretchY, state.rotate, state.flip, width, height);
            }
        }
    }

    private static void drawPhoto(Texture photo, float opacity, float x, float y, float scale, float stretchX, float stretchY, float rotate, float flip, int width, int height)
    {
        if (photo == null || photo.width <= 0 || photo.height <= 0 || opacity <= 0F)
        {
            return;
        }

        float halfW = scale * stretchX * (photo.width / (float) photo.height) * (height / (float) width);
        float halfH = scale * stretchY;

        /* Positive degrees turn the photo clockwise on screen, hence the minus:
         * the setting's Y axis points down while NDC's points up */
        float angle = MathUtils.toRad(-rotate);

        GL20.glUniform4f(uniformTransform, x, -y, halfW, halfH);
        GL20.glUniform2f(uniformRotation, (float) Math.cos(angle), (float) Math.sin(angle));
        GL20.glUniform1f(uniformAspect, width / (float) height);
        GL20.glUniform1f(uniformOpacity, opacity);
        GL20.glUniform1f(uniformPhotoFlip, Math.round(flip));
        photo.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }

    /** Photo layers contributed by photo clips playing on the camera timeline right now. */
    private static List<PhotoClip.State> getClipPhotoStates()
    {
        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            return PhotoClip.getStates(controller.getContext());
        }

        return Collections.emptyList();
    }

    /**
     * The film filter values effective this frame: the settings' sliders, with any
     * channel animated by a playing {@link FilterClip} overriding its slider.
     */
    private static FilterState getFilterState()
    {
        Map<String, Double> overrides = null;

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            overrides = FilterClip.getValues(controller.getContext());
        }

        FilterState state = new FilterState();

        state.brightness = filterValue(overrides, "brightness", BBSSettings.filmFilterBrightness, 0F);
        state.contrast = filterValue(overrides, "contrast", BBSSettings.filmFilterContrast, 0F);
        state.saturation = filterValue(overrides, "saturation", BBSSettings.filmFilterSaturation, 0F);
        state.hue = filterValue(overrides, "hue", BBSSettings.filmFilterHue, 0F);
        state.temperature = filterValue(overrides, "temperature", BBSSettings.filmFilterTemperature, 0F);
        state.gamma = filterValue(overrides, "gamma", BBSSettings.filmFilterGamma, 1F);
        state.sharpness = filterValue(overrides, "sharpness", BBSSettings.filmFilterSharpness, 0F);
        state.vignette = filterValue(overrides, "vignette", BBSSettings.filmFilterVignette, 0F);
        state.sepia = filterValue(overrides, "sepia", BBSSettings.filmFilterSepia, 0F);
        state.grain = filterValue(overrides, "grain", BBSSettings.filmFilterGrain, 0F);
        state.aberration = filterValue(overrides, "aberration", BBSSettings.filmFilterAberration, 0F);
        state.invert = filterValue(overrides, "invert", BBSSettings.filmFilterInvert, 0F);
        state.posterize = filterValue(overrides, "posterize", BBSSettings.filmFilterPosterize, 0F);
        state.pixelate = filterValue(overrides, "pixelate", BBSSettings.filmFilterPixelate, 0F);
        state.distortion = filterValue(overrides, "distortion", BBSSettings.filmFilterDistortion, 0F);
        state.bloom = filterValue(overrides, "bloom", BBSSettings.filmFilterBloom, 0F);
        state.radial = filterValue(overrides, "radial", BBSSettings.filmFilterRadial, 0F);
        state.vhs = filterValue(overrides, "vhs", BBSSettings.filmFilterVhs, 0F);
        state.flip = filterValue(overrides, "flip", BBSSettings.filmFilterFlip, 0F);
        state.fisheye = filterValue(overrides, "fisheye", BBSSettings.filmFilterFisheye, 0F);

        return state;
    }

    private static float filterValue(Map<String, Double> overrides, String id, ValueFloat setting, float fallback)
    {
        if (overrides != null)
        {
            Double value = overrides.get(id);

            if (value != null && setting != null)
            {
                return MathUtils.clamp(value.floatValue(), setting.getMin(), setting.getMax());
            }
        }

        return setting == null ? fallback : setting.get();
    }

    /**
     * Grain follows the film's clock when one is playing, so an exported video
     * gets the exact same noise every render; otherwise it just runs on real time.
     */
    private static float getGrainSeed()
    {
        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            ClipContext context = controller.getContext();

            return (context.ticks % 1000) * 7.31F + context.transition * 7.31F;
        }

        return (System.currentTimeMillis() % 100000L) / 50F;
    }

    private static void ensureGeometry()
    {
        if (vao != 0)
        {
            return;
        }

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1F, -1F, 1F, -1F, -1F, 1F, 1F, 1F}, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);
    }

    private static void ensureFilterProgram()
    {
        if (filterProgram != 0)
        {
            return;
        }

        filterProgram = compileProgram(FILTER_VERTEX, FILTER_FRAGMENT);
        uniformTexel = GL20.glGetUniformLocation(filterProgram, "u_texel");
        uniformBrightness = GL20.glGetUniformLocation(filterProgram, "u_brightness");
        uniformContrast = GL20.glGetUniformLocation(filterProgram, "u_contrast");
        uniformSaturation = GL20.glGetUniformLocation(filterProgram, "u_saturation");
        uniformHue = GL20.glGetUniformLocation(filterProgram, "u_hue");
        uniformTemperature = GL20.glGetUniformLocation(filterProgram, "u_temperature");
        uniformGamma = GL20.glGetUniformLocation(filterProgram, "u_gamma");
        uniformSharpness = GL20.glGetUniformLocation(filterProgram, "u_sharpness");
        uniformVignette = GL20.glGetUniformLocation(filterProgram, "u_vignette");
        uniformSepia = GL20.glGetUniformLocation(filterProgram, "u_sepia");
        uniformGrain = GL20.glGetUniformLocation(filterProgram, "u_grain");
        uniformAberration = GL20.glGetUniformLocation(filterProgram, "u_aberration");
        uniformInvert = GL20.glGetUniformLocation(filterProgram, "u_invert");
        uniformPosterize = GL20.glGetUniformLocation(filterProgram, "u_posterize");
        uniformPixelate = GL20.glGetUniformLocation(filterProgram, "u_pixelate");
        uniformDistortion = GL20.glGetUniformLocation(filterProgram, "u_distortion");
        uniformBloomStrength = GL20.glGetUniformLocation(filterProgram, "u_bloom_strength");
        uniformRadial = GL20.glGetUniformLocation(filterProgram, "u_radial");
        uniformVhs = GL20.glGetUniformLocation(filterProgram, "u_vhs");
        uniformFlip = GL20.glGetUniformLocation(filterProgram, "u_flip");
        uniformFisheye = GL20.glGetUniformLocation(filterProgram, "u_fisheye");
        uniformSeed = GL20.glGetUniformLocation(filterProgram, "u_seed");

        /* The blurred highlights always sit on texture unit 1 */
        GL20.glUseProgram(filterProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(filterProgram, "u_bloom"), 1);
    }

    private static void ensurePhotoProgram()
    {
        if (photoProgram != 0)
        {
            return;
        }

        photoProgram = compileProgram(PHOTO_VERTEX, PHOTO_FRAGMENT);
        uniformTransform = GL20.glGetUniformLocation(photoProgram, "u_transform");
        uniformRotation = GL20.glGetUniformLocation(photoProgram, "u_rotation");
        uniformAspect = GL20.glGetUniformLocation(photoProgram, "u_aspect");
        uniformOpacity = GL20.glGetUniformLocation(photoProgram, "u_opacity");
        uniformPhotoFlip = GL20.glGetUniformLocation(photoProgram, "u_flip");
    }

    private static void ensurePingTexture(int width, int height)
    {
        if (pingTexture == null)
        {
            pingTexture = new Texture();
            pingTexture.setFormat(TextureFormat.RGB_U8);
            pingTexture.setFilter(GL11.GL_NEAREST);
        }

        if (pingTexture.width != width || pingTexture.height != height)
        {
            pingTexture.bind();
            pingTexture.setSize(width, height);
        }
    }

    private static void ensureBloomResources(int width, int height)
    {
        if (bloomCutProgram == 0)
        {
            bloomCutProgram = compileProgram(FILTER_VERTEX, BLOOM_CUT_FRAGMENT);
            uniformBloomCutTexel = GL20.glGetUniformLocation(bloomCutProgram, "u_texel");
        }

        if (bloomBlurProgram == 0)
        {
            bloomBlurProgram = compileProgram(FILTER_VERTEX, BLOOM_BLUR_FRAGMENT);
            uniformBlurDirection = GL20.glGetUniformLocation(bloomBlurProgram, "u_direction");
        }

        if (bloomFramebuffer == 0)
        {
            bloomFramebuffer = GL30.glGenFramebuffers();
        }

        bloomTextureA = ensureBloomTexture(bloomTextureA, width, height);
        bloomTextureB = ensureBloomTexture(bloomTextureB, width, height);
    }

    private static Texture ensureBloomTexture(Texture texture, int width, int height)
    {
        if (texture == null)
        {
            texture = new Texture();
            texture.setFormat(TextureFormat.RGB_U8);

            /* Linear so the quarter-res glow stretches back up smoothly */
            texture.setFilter(GL11.GL_LINEAR);
        }

        if (texture.width != width || texture.height != height)
        {
            texture.bind();
            texture.setSize(width, height);
        }

        return texture;
    }

    private static int compileProgram(String vertexSource, String fragmentSource)
    {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();

        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glBindAttribLocation(program, 0, "a_position");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
        {
            throw new IllegalStateException("Failed to link a film effects program: " + GL20.glGetProgramInfoLog(program));
        }

        /* The main sampler always reads from texture unit 0 */
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "u_texture"), 0);

        return program;
    }

    private static int compileShader(int type, String source)
    {
        int shader = GL20.glCreateShader(type);

        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            throw new IllegalStateException("Failed to compile a film effects shader: " + GL20.glGetShaderInfoLog(shader));
        }

        return shader;
    }

    /** The film filter values effective this frame, sliders and clip overrides merged. */
    private static class FilterState
    {
        public float brightness;
        public float contrast;
        public float saturation;
        public float hue;
        public float temperature;
        public float gamma = 1F;
        public float sharpness;
        public float vignette;
        public float sepia;
        public float grain;
        public float aberration;
        public float invert;
        public float posterize;
        public float pixelate;
        public float distortion;
        public float bloom;
        public float radial;
        public float vhs;
        public float flip;
        public float fisheye;
    }
}
