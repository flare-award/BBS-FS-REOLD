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
import mchorse.bbs_mod.forms.forms.FilterBoardForm;
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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
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
        out vec2 v_mask_uv;
        out vec2 v_filter_uv;

        void main()
        {
            v_uv = a_position * 0.5 + 0.5;
            v_mask_uv = v_uv;
            v_filter_uv = v_uv;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }""";

    private static final String BOARD_VERTEX = """
        #version 150

        in vec4 a_position;
        in vec2 a_mask_uv;
        in vec2 a_filter_uv;

        out vec2 v_mask_uv;
        out vec2 v_filter_uv;

        void main()
        {
            v_mask_uv = a_mask_uv;
            v_filter_uv = a_filter_uv;
            gl_Position = a_position;
        }""";

    private static final String FILTER_FRAGMENT = """
        #version 150

        uniform sampler2D u_texture;
        uniform sampler2D u_bloom;
        uniform sampler2D u_mask;
        uniform float u_mask_enabled;
        uniform float u_mask_opacity;
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
        uniform float u_board_mode;
        uniform vec2 u_board_origin;
        uniform vec2 u_board_axis_x;
        uniform vec2 u_board_axis_y;
        uniform vec2 u_board_viewport_origin;
        uniform vec3 u_board_homography_0;
        uniform vec3 u_board_homography_1;
        uniform vec3 u_board_homography_2;

        in vec2 v_mask_uv;
        in vec2 v_filter_uv;

        out vec4 fragColor;

        vec3 hueShift(vec3 color, float angle)
        {
            const vec3 k = vec3(0.57735);
            float c = cos(angle);

            return color * c + cross(k, color) * sin(angle) + k * dot(k, color) * (1.0 - c);
        }

        vec2 mapBoardUv(vec2 localUv)
        {
            if (u_board_mode > 0.5)
            {
                vec3 projected = vec3(
                    dot(u_board_homography_0, vec3(localUv, 1.0)),
                    dot(u_board_homography_1, vec3(localUv, 1.0)),
                    dot(u_board_homography_2, vec3(localUv, 1.0)));

                return projected.xy / projected.z;
            }

            return localUv;
        }

        vec3 sampleFrame(vec2 uv)
        {
            if (u_aberration > 0.0)
            {
                /* Radial fringing: red samples outward, blue inward */
                vec2 center = u_board_mode > 0.5 ? mapBoardUv(vec2(0.5)) : vec2(0.5);
                vec2 shift = (uv - center) * u_aberration * 0.03;

                return vec3(
                    texture(u_texture, uv + shift).r,
                    texture(u_texture, uv).g,
                    texture(u_texture, uv - shift).b);
            }

            return texture(u_texture, uv).rgb;
        }

        void main()
        {
            vec2 effectUv = v_filter_uv;
            vec2 uv = (gl_FragCoord.xy - u_board_viewport_origin) * u_texel;

            if (u_flip == 1.0)
            {
                effectUv.y = 1.0 - effectUv.y;
                uv = u_board_mode > 0.5 ? mapBoardUv(effectUv) : vec2(uv.x, 1.0 - uv.y);
            }
            else if (u_flip == 2.0)
            {
                effectUv.x = 1.0 - effectUv.x;
                uv = u_board_mode > 0.5 ? mapBoardUv(effectUv) : vec2(1.0 - uv.x, uv.y);
            }

            float aspect = u_texel.y / u_texel.x;
            vec2 filterTexel = u_texel;

            if (u_board_mode > 0.5)
            {
                vec2 pixelsX = u_board_axis_x / u_texel;
                vec2 pixelsY = u_board_axis_y / u_texel;
                float pixelsWidth = max(length(pixelsX), 1.0);
                float pixelsHeight = max(length(pixelsY), 1.0);

                filterTexel = vec2(1.0 / pixelsWidth, 1.0 / pixelsHeight);
                aspect = pixelsWidth / pixelsHeight;
            }

            if (u_distortion != 0.0)
            {
                /* Barrel (positive) or pincushion (negative) lens warp,
                 * aspect-corrected so the bulge stays circular */
                vec2 d = effectUv - 0.5;

                d.x *= aspect;
                d *= 1.0 + u_distortion * dot(d, d) * 1.5;
                d.x /= aspect;
                effectUv = clamp(d + 0.5, 0.0, 1.0);
                uv = u_board_mode > 0.5 ? mapBoardUv(effectUv) : effectUv;
            }

            if (u_fisheye != 0.0)
            {
                /* Positive bulges the center out like a fisheye lens, negative pinches it in */
                vec2 d = effectUv - 0.5;

                d.x *= aspect;

                float r = length(d) * 1.4142;

                if (r > 0.0001)
                {
                    d *= pow(r, 1.0 + u_fisheye * 0.75) / r;
                }

                d.x /= aspect;
                effectUv = clamp(d + 0.5, 0.0, 1.0);
                uv = u_board_mode > 0.5 ? mapBoardUv(effectUv) : effectUv;
            }

            if (u_pixelate >= 1.0)
            {
                /* Snap the sample point to the center of a u_pixelate-sized cell */
                vec2 cell = filterTexel * u_pixelate;

                effectUv = (floor(effectUv / cell) + 0.5) * cell;
                uv = u_board_mode > 0.5 ? mapBoardUv(effectUv) : effectUv;
            }

            if (u_vhs > 0.0)
            {
                /* Per-scanline horizontal jitter, tape-style: a few sines at odd
                 * frequencies, seeded by the film clock so exports are stable */
                float line = floor(effectUv.y / filterTexel.y);
                float wobble = sin(line * 0.35 + u_seed * 0.63) * sin(line * 0.043 + u_seed * 0.121);

                if (u_board_mode > 0.5)
                {
                    uv += u_board_axis_x * wobble * filterTexel.x * u_vhs * 4.0;
                }
                else
                {
                    uv.x += wobble * u_texel.x * u_vhs * 4.0;
                }
            }

            vec3 color = sampleFrame(uv);

            if (u_radial > 0.0)
            {
                /* Zoom blur: smear samples toward the frame's center */
                vec2 center = u_board_mode > 0.5 ? mapBoardUv(vec2(0.5)) : vec2(0.5);
                vec2 toCenter = (center - uv) * u_radial * 0.12;
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
                vec2 bleed = u_board_mode > 0.5
                    ? u_board_axis_x * filterTexel.x * u_vhs * 2.0
                    : vec2(u_texel.x * u_vhs * 2.0, 0.0);

                color.r = mix(color.r, sampleFrame(uv + bleed).r, 0.75);
                color.b = mix(color.b, sampleFrame(uv - bleed).b, 0.75);
                color *= 1.0 - u_vhs * 0.2 * (0.5 + 0.5 * sin(effectUv.y / filterTexel.y * 3.14159));
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
                float dist = distance(v_filter_uv, vec2(0.5)) * 1.4142;
                float mask = abs(u_vignette) * smoothstep(0.35, 1.1, dist);

                color = mix(color, u_vignette > 0.0 ? vec3(1.0) : vec3(0.0), mask);
            }

            if (u_grain > 0.0)
            {
                /* Animated monochrome noise, one random value per output pixel */
                vec2 pixel = floor(effectUv / filterTexel) + u_seed;
                float noise = fract(sin(dot(pixel, vec2(12.9898, 78.233))) * 43758.5453);

                color += (noise - 0.5) * u_grain * 0.3;
            }

            float alpha = 1.0;

            if (u_mask_enabled > 0.5)
            {
                alpha = texture(u_mask, v_mask_uv).a * u_mask_opacity;

                if (alpha <= 0.001)
                {
                    discard;
                }
            }

            fragColor = vec4(clamp(color, 0.0, 1.0), alpha);
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

    /* A board shader failure disables only board lenses; the regular film pass can continue. */
    private static boolean boardBroken;

    /* Guards the snapshot pass against accidental nested board captures. */
    private static boolean renderingFilterBoard;

    /* All boards in one world pass sample one immutable pre-board snapshot, so a board
     * never captures an earlier board and applies its filters a second time. */
    private static boolean boardSnapshotReady;
    private static int boardSnapshotFramebuffer = -1;
    private static int boardSnapshotWidth;
    private static int boardSnapshotHeight;

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
    private static int boardVao;
    private static int boardVbo;
    private static int boardProgram;
    private static FilterUniforms filterUniforms;
    private static FilterUniforms boardUniforms;
    private static int photoProgram;
    private static int bloomCutProgram;
    private static int bloomBlurProgram;
    private static int bloomFramebuffer;
    private static Texture pingTexture;
    private static Texture bloomTextureA;
    private static Texture bloomTextureB;

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
    public static void beginFilterBoardFrame()
    {
        boardSnapshotReady = false;
        boardSnapshotFramebuffer = -1;
        boardSnapshotWidth = 0;
        boardSnapshotHeight = 0;
    }

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
        copyFrame(framebuffer.id, width, height);

        RenderSystem.disableBlend();

        boolean bloom = isActive(state.bloom, 0F);

        if (bloom)
        {
            applyBloom(framebuffer.id, width, height);
        }

        GL20.glUseProgram(filterProgram);
        setFilterUniforms(filterUniforms, state, width, height, bloom);

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
     * Draw a FilterBoard from the immutable pre-board snapshot for this world
     * pass. The positions are already in clip space, which preserves the normal
     * world depth test while keeping the filter itself screen-local.
     */
    public static void renderFilterBoard(FilterBoardForm form, Texture mask, float[] positions, float[] maskUV, float[] filterUV, float opacity, boolean linear, boolean mipmap)
    {
        if (broken || boardBroken || renderingFilterBoard || form == null || mask == null || positions == null || maskUV == null || filterUV == null)
        {
            return;
        }

        int framebufferId = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        int width = viewport[2];
        int height = viewport[3];

        if (framebufferId == 0 || width <= 0 || height <= 0 || opacity <= 0F)
        {
            return;
        }

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture0 = getTextureBinding(GL13.GL_TEXTURE0);
        int previousTexture1 = getTextureBinding(GL13.GL_TEXTURE1);
        int previousTexture2 = getTextureBinding(GL13.GL_TEXTURE2);
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        renderingFilterBoard = true;

        try
        {
            ensureBoardProgram();
            ensureBoardGeometry();

            if (!boardSnapshotReady || boardSnapshotFramebuffer != framebufferId
                || boardSnapshotWidth != width || boardSnapshotHeight != height)
            {
                copyFrame(framebufferId, width, height);
                boardSnapshotReady = true;
                boardSnapshotFramebuffer = framebufferId;
                boardSnapshotWidth = width;
                boardSnapshotHeight = height;
            }

            FilterState state = getFilterState(form);
            boolean bloom = isActive(state.bloom, 0F);

            RenderSystem.disableBlend();

            if (bloom)
            {
                applyBloom(framebufferId, width, height);
            }

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebufferId);
            GL30.glBindVertexArray(boardVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, boardVbo);
            uploadBoardGeometry(positions, maskUV, filterUV);

            GL20.glUseProgram(boardProgram);
            setFilterUniforms(boardUniforms, state, width, height, bloom);
            GL20.glUniform1f(boardUniforms.maskEnabled, 1F);
            GL20.glUniform1f(boardUniforms.maskOpacity, opacity);
            setBoardMapping(boardUniforms, positions, viewport[0], viewport[1]);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            mask.bind();
            mask.setFilterMipmap(linear, mipmap);

            if (bloom)
            {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                bloomTextureA.bind();
            }

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            pingTexture.bind();
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        }
        catch (Exception e)
        {
            boardBroken = true;

            e.printStackTrace();
        }
        finally
        {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            mask.bind();
            mask.setFilterMipmap(false, false);

            if (previousBlend)
            {
                RenderSystem.enableBlend();
            }
            else
            {
                RenderSystem.disableBlend();
            }

            if (previousDepth)
            {
                RenderSystem.enableDepthTest();
            }
            else
            {
                RenderSystem.disableDepthTest();
            }

            if (previousCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            RenderSystem.depthFunc(previousDepthFunc);
            RenderSystem.depthMask(previousDepthMask);
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL20.glUseProgram(previousProgram);
            setTextureBinding(GL13.GL_TEXTURE2, previousTexture2);
            setTextureBinding(GL13.GL_TEXTURE1, previousTexture1);
            setTextureBinding(GL13.GL_TEXTURE0, previousTexture0);
            GL13.glActiveTexture(previousActiveTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            renderingFilterBoard = false;
        }
    }

    private static void copyFrame(int framebufferId, int width, int height)
    {
        ensurePingTexture(width, height);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        pingTexture.bind();
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    private static int getTextureBinding(int texture)
    {
        GL13.glActiveTexture(texture);

        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static void setTextureBinding(int texture, int binding)
    {
        GL13.glActiveTexture(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
    }

    private static void uploadBoardGeometry(float[] positions, float[] maskUV, float[] filterUV)
    {
        int count = Math.min(positions.length / 4, Math.min(maskUV.length / 2, filterUV.length / 2));
        float[] data = new float[count * 8];

        for (int i = 0; i < count; i++)
        {
            int positionIndex = i * 4;
            int uvIndex = i * 2;
            int dataIndex = i * 8;

            data[dataIndex] = positions[positionIndex];
            data[dataIndex + 1] = positions[positionIndex + 1];
            data[dataIndex + 2] = positions[positionIndex + 2];
            data[dataIndex + 3] = positions[positionIndex + 3];
            data[dataIndex + 4] = maskUV[uvIndex];
            data[dataIndex + 5] = maskUV[uvIndex + 1];
            data[dataIndex + 6] = filterUV[uvIndex];
            data[dataIndex + 7] = filterUV[uvIndex + 1];
        }

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
    }

    private static void setBoardMapping(FilterUniforms uniforms, float[] positions, int viewportX, int viewportY)
    {
        float[] topLeft = screenPosition(positions, 2);
        float[] topRight = screenPosition(positions, 1);
        float[] bottomLeft = screenPosition(positions, 0);
        float[] bottomRight = screenPosition(positions, 4);
        float dx1 = topRight[0] - bottomRight[0];
        float dx2 = bottomLeft[0] - bottomRight[0];
        float dx3 = topLeft[0] - topRight[0] + bottomRight[0] - bottomLeft[0];
        float dy1 = topRight[1] - bottomRight[1];
        float dy2 = bottomLeft[1] - bottomRight[1];
        float dy3 = topLeft[1] - topRight[1] + bottomRight[1] - bottomLeft[1];
        float denominator = dx1 * dy2 - dx2 * dy1;
        float g = 0F;
        float h = 0F;

        if (Math.abs(denominator) > 0.000001F)
        {
            g = (dx3 * dy2 - dx2 * dy3) / denominator;
            h = (dx1 * dy3 - dx3 * dy1) / denominator;
        }

        float a = topRight[0] * (g + 1F) - topLeft[0];
        float b = bottomLeft[0] * (h + 1F) - topLeft[0];
        float d = topRight[1] * (g + 1F) - topLeft[1];
        float e = bottomLeft[1] * (h + 1F) - topLeft[1];

        GL20.glUniform1f(uniforms.boardMode, 1F);
        GL20.glUniform2f(uniforms.boardOrigin, topLeft[0], topLeft[1]);
        GL20.glUniform2f(uniforms.boardAxisX, topRight[0] - topLeft[0], topRight[1] - topLeft[1]);
        GL20.glUniform2f(uniforms.boardAxisY, bottomLeft[0] - topLeft[0], bottomLeft[1] - topLeft[1]);
        GL20.glUniform2f(uniforms.boardViewportOrigin, viewportX, viewportY);
        GL20.glUniform3f(uniforms.boardHomography0, a, b, topLeft[0]);
        GL20.glUniform3f(uniforms.boardHomography1, d, e, topLeft[1]);
        GL20.glUniform3f(uniforms.boardHomography2, g, h, 1F);
    }

    private static float[] screenPosition(float[] positions, int vertex)
    {
        int index = vertex * 4;
        float w = positions[index + 3];
        float reciprocalW = 1F / (Math.abs(w) < 0.000001F ? (w < 0F ? -0.000001F : 0.000001F) : w);

        return new float[] {
            positions[index] * reciprocalW * 0.5F + 0.5F,
            positions[index + 1] * reciprocalW * 0.5F + 0.5F
        };
    }

    /**
     * Fills {@link #bloomTextureA} with the frame's blurred highlights: threshold
     * cut at quarter resolution, then one horizontal and one vertical gaussian pass.
     */
    private static void applyBloom(int framebufferId, int width, int height)
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

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
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

    /** Whether a photo needs an explicit world ordering pass instead of the final overlay. */
    private static boolean hasOrderedWorldPhotos()
    {
        return hasWorldPhoto(LAYER_BEHIND_ACTORS)
            || hasWorldPhoto(LAYER_BEHIND_BLOCKS)
            || hasWorldPhoto(LAYER_BEHIND_MODELS);
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
            && !BBSRendering.isIrisShadowPass() && isEffectsActive() && hasOrderedWorldPhotos();
    }

    /**
     * Whether vanilla's entity renderer should leave an entity out of the film preview's
     * photo composition. The world renderer visits ordinary entities before the AFTER_ENTITIES
     * callback where this mod places the photo, so a depth fence cannot remove their already
     * rasterized pixels. Selected film actors are the only exception: their real entities are
     * the actor replays' visible bodies and must remain available for the chosen layer mode.
     */
    public static boolean shouldHideWorldEntity(Entity entity)
    {
        if (broken || showNoPhoto || BBSRendering.isIrisShadowPass() || !isEffectsActive())
        {
            return false;
        }

        if (!hasOrderedWorldPhotos())
        {
            return false;
        }

        if (currentFilm == null)
        {
            return true;
        }

        Map<String, Integer> actors = BBSModClient.getFilms().actors.get(currentFilm.getId());

        return actors == null || !actors.containsValue(entity.getId());
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

    private static FilterState getFilterState(FilterBoardForm form)
    {
        FilterState state = new FilterState();

        state.brightness = form.brightness.get();
        state.contrast = form.contrast.get();
        state.saturation = form.filterSaturation.get();
        state.hue = form.filterHue.get();
        state.temperature = form.temperature.get();
        state.gamma = form.gamma.get();
        state.sharpness = form.sharpness.get();
        state.vignette = form.vignette.get();
        state.sepia = form.sepia.get();
        state.grain = form.grain.get();
        state.aberration = form.aberration.get();
        state.invert = form.invert.get();
        state.posterize = form.posterize.get();
        state.pixelate = form.pixelate.get();
        state.distortion = form.distortion.get();
        state.bloom = form.bloom.get();
        state.radial = form.radial.get();
        state.vhs = form.vhs.get();
        state.flip = form.flip.get();
        state.fisheye = form.fisheye.get();

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
        filterUniforms = locateFilterUniforms(filterProgram);

        /* The blurred highlights always sit on texture unit 1; boards use unit 2
         * for their alpha mask. */
        GL20.glUseProgram(filterProgram);
        GL20.glUniform1i(filterUniforms.bloomSampler, 1);
        GL20.glUniform1i(filterUniforms.maskSampler, 2);
    }

    private static void ensureBoardProgram()
    {
        if (boardProgram != 0)
        {
            return;
        }

        boardProgram = compileProgram(BOARD_VERTEX, FILTER_FRAGMENT, true);
        boardUniforms = locateFilterUniforms(boardProgram);

        GL20.glUseProgram(boardProgram);
        GL20.glUniform1i(boardUniforms.bloomSampler, 1);
        GL20.glUniform1i(boardUniforms.maskSampler, 2);
    }

    private static FilterUniforms locateFilterUniforms(int program)
    {
        FilterUniforms uniforms = new FilterUniforms();

        uniforms.texel = GL20.glGetUniformLocation(program, "u_texel");
        uniforms.brightness = GL20.glGetUniformLocation(program, "u_brightness");
        uniforms.contrast = GL20.glGetUniformLocation(program, "u_contrast");
        uniforms.saturation = GL20.glGetUniformLocation(program, "u_saturation");
        uniforms.hue = GL20.glGetUniformLocation(program, "u_hue");
        uniforms.temperature = GL20.glGetUniformLocation(program, "u_temperature");
        uniforms.gamma = GL20.glGetUniformLocation(program, "u_gamma");
        uniforms.sharpness = GL20.glGetUniformLocation(program, "u_sharpness");
        uniforms.vignette = GL20.glGetUniformLocation(program, "u_vignette");
        uniforms.sepia = GL20.glGetUniformLocation(program, "u_sepia");
        uniforms.grain = GL20.glGetUniformLocation(program, "u_grain");
        uniforms.aberration = GL20.glGetUniformLocation(program, "u_aberration");
        uniforms.invert = GL20.glGetUniformLocation(program, "u_invert");
        uniforms.posterize = GL20.glGetUniformLocation(program, "u_posterize");
        uniforms.pixelate = GL20.glGetUniformLocation(program, "u_pixelate");
        uniforms.distortion = GL20.glGetUniformLocation(program, "u_distortion");
        uniforms.bloomStrength = GL20.glGetUniformLocation(program, "u_bloom_strength");
        uniforms.radial = GL20.glGetUniformLocation(program, "u_radial");
        uniforms.vhs = GL20.glGetUniformLocation(program, "u_vhs");
        uniforms.flip = GL20.glGetUniformLocation(program, "u_flip");
        uniforms.fisheye = GL20.glGetUniformLocation(program, "u_fisheye");
        uniforms.seed = GL20.glGetUniformLocation(program, "u_seed");
        uniforms.bloomSampler = GL20.glGetUniformLocation(program, "u_bloom");
        uniforms.maskSampler = GL20.glGetUniformLocation(program, "u_mask");
        uniforms.maskEnabled = GL20.glGetUniformLocation(program, "u_mask_enabled");
        uniforms.maskOpacity = GL20.glGetUniformLocation(program, "u_mask_opacity");
        uniforms.boardMode = GL20.glGetUniformLocation(program, "u_board_mode");
        uniforms.boardOrigin = GL20.glGetUniformLocation(program, "u_board_origin");
        uniforms.boardAxisX = GL20.glGetUniformLocation(program, "u_board_axis_x");
        uniforms.boardAxisY = GL20.glGetUniformLocation(program, "u_board_axis_y");
        uniforms.boardViewportOrigin = GL20.glGetUniformLocation(program, "u_board_viewport_origin");
        uniforms.boardHomography0 = GL20.glGetUniformLocation(program, "u_board_homography_0");
        uniforms.boardHomography1 = GL20.glGetUniformLocation(program, "u_board_homography_1");
        uniforms.boardHomography2 = GL20.glGetUniformLocation(program, "u_board_homography_2");

        return uniforms;
    }

    private static void setFilterUniforms(FilterUniforms uniforms, FilterState state, int width, int height, boolean bloom)
    {
        GL20.glUniform2f(uniforms.texel, 1F / width, 1F / height);
        GL20.glUniform1f(uniforms.brightness, 1F + state.brightness);
        GL20.glUniform1f(uniforms.contrast, 1F + state.contrast);
        GL20.glUniform1f(uniforms.saturation, 1F + state.saturation);
        GL20.glUniform1f(uniforms.hue, MathUtils.toRad(state.hue));
        GL20.glUniform1f(uniforms.temperature, state.temperature * TEMPERATURE_STRENGTH);
        GL20.glUniform1f(uniforms.gamma, 1F / state.gamma);
        GL20.glUniform1f(uniforms.sharpness, state.sharpness * SHARPNESS_STRENGTH);
        GL20.glUniform1f(uniforms.vignette, state.vignette);
        GL20.glUniform1f(uniforms.sepia, state.sepia);
        GL20.glUniform1f(uniforms.grain, state.grain);
        GL20.glUniform1f(uniforms.aberration, state.aberration);
        GL20.glUniform1f(uniforms.invert, state.invert);
        GL20.glUniform1f(uniforms.posterize, (float) Math.floor(state.posterize));
        GL20.glUniform1f(uniforms.pixelate, (float) Math.floor(state.pixelate));
        GL20.glUniform1f(uniforms.distortion, state.distortion);
        GL20.glUniform1f(uniforms.bloomStrength, bloom ? state.bloom : 0F);
        GL20.glUniform1f(uniforms.radial, state.radial);
        GL20.glUniform1f(uniforms.vhs, state.vhs);
        GL20.glUniform1f(uniforms.flip, Math.round(state.flip));
        GL20.glUniform1f(uniforms.fisheye, state.fisheye);
        GL20.glUniform1f(uniforms.seed, getGrainSeed());
        GL20.glUniform1f(uniforms.maskEnabled, 0F);
        GL20.glUniform1f(uniforms.maskOpacity, 1F);
        GL20.glUniform1f(uniforms.boardMode, 0F);
        GL20.glUniform2f(uniforms.boardOrigin, 0F, 0F);
        GL20.glUniform2f(uniforms.boardAxisX, 1F, 0F);
        GL20.glUniform2f(uniforms.boardAxisY, 0F, 1F);
        GL20.glUniform2f(uniforms.boardViewportOrigin, 0F, 0F);
        GL20.glUniform3f(uniforms.boardHomography0, 1F, 0F, 0F);
        GL20.glUniform3f(uniforms.boardHomography1, 0F, 1F, 0F);
        GL20.glUniform3f(uniforms.boardHomography2, 0F, 0F, 1F);
    }

    private static void ensureBoardGeometry()
    {
        if (boardVao != 0)
        {
            return;
        }

        boardVao = GL30.glGenVertexArrays();
        boardVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(boardVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, boardVbo);
        int stride = 8 * Float.BYTES;

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 4, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 4L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 6L * Float.BYTES);
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
        return compileProgram(vertexSource, fragmentSource, false);
    }

    private static int compileProgram(String vertexSource, String fragmentSource, boolean board)
    {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();

        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glBindAttribLocation(program, 0, "a_position");

        if (board)
        {
            GL20.glBindAttribLocation(program, 1, "a_mask_uv");
            GL20.glBindAttribLocation(program, 2, "a_filter_uv");
        }

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

    private static class FilterUniforms
    {
        public int texel;
        public int brightness;
        public int contrast;
        public int saturation;
        public int hue;
        public int temperature;
        public int gamma;
        public int sharpness;
        public int vignette;
        public int sepia;
        public int grain;
        public int aberration;
        public int invert;
        public int posterize;
        public int pixelate;
        public int distortion;
        public int bloomStrength;
        public int radial;
        public int vhs;
        public int flip;
        public int fisheye;
        public int seed;
        public int bloomSampler;
        public int maskSampler;
        public int maskEnabled;
        public int maskOpacity;
        public int boardMode;
        public int boardOrigin;
        public int boardAxisX;
        public int boardAxisY;
        public int boardViewportOrigin;
        public int boardHomography0;
        public int boardHomography1;
        public int boardHomography2;
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
