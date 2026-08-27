package mchorse.bbs_mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Post-processing baked into the film's export texture.
 *
 * <p>Right after {@link BBSRendering#onRenderBeforeScreen()} blits the frame into the
 * export framebuffer, this class color-grades it with a fullscreen shader pass and lays
 * a photo over it. The film preview, the video recorder and the screenshot all read that
 * very texture, so whatever happens here shows up in every one of them at once.</p>
 */
public class FilmEffects
{
    /** Below this distance from its neutral value a filter is considered off. */
    private static final float NEUTRAL_EPSILON = 0.0001F;

    /** How much the sharpness slider's full swing weighs in the unsharp mask. */
    private static final float SHARPNESS_STRENGTH = 2F;

    /** How far the temperature slider's full swing pushes the red/blue channels. */
    private static final float TEMPERATURE_STRENGTH = 0.2F;

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

        in vec2 v_uv;

        out vec4 fragColor;

        vec3 hueShift(vec3 color, float angle)
        {
            const vec3 k = vec3(0.57735);
            float c = cos(angle);

            return color * c + cross(k, color) * sin(angle) + k * dot(k, color) * (1.0 - c);
        }

        void main()
        {
            vec3 color = texture(u_texture, v_uv).rgb;

            if (u_sharpness > 0.0)
            {
                vec3 blur = texture(u_texture, v_uv + vec2(u_texel.x, 0.0)).rgb;

                blur += texture(u_texture, v_uv - vec2(u_texel.x, 0.0)).rgb;
                blur += texture(u_texture, v_uv + vec2(0.0, u_texel.y)).rgb;
                blur += texture(u_texture, v_uv - vec2(0.0, u_texel.y)).rgb;
                color += (color - blur * 0.25) * u_sharpness;
            }

            color *= u_brightness;
            color = (color - 0.5) * u_contrast + 0.5;
            color += vec3(u_temperature, 0.0, -u_temperature);

            float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

            color = mix(vec3(luma), color, u_saturation);
            color = hueShift(color, u_hue);
            color = pow(max(color, vec3(0.0)), vec3(u_gamma));

            vec3 sepia = vec3(
                dot(color, vec3(0.393, 0.769, 0.189)),
                dot(color, vec3(0.349, 0.686, 0.168)),
                dot(color, vec3(0.272, 0.534, 0.131)));

            color = mix(color, sepia, u_sepia);

            if (u_vignette > 0.0)
            {
                float dist = distance(v_uv, vec2(0.5)) * 1.4142;

                color *= 1.0 - u_vignette * smoothstep(0.35, 1.1, dist);
            }

            fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }""";

    private static final String PHOTO_VERTEX = """
        #version 150

        uniform vec4 u_transform;

        in vec2 a_position;

        out vec2 v_uv;

        void main()
        {
            v_uv = vec2(a_position.x * 0.5 + 0.5, 0.5 - a_position.y * 0.5);
            gl_Position = vec4(u_transform.xy + a_position * u_transform.zw, 0.0, 1.0);
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

    private static int vao;
    private static int vbo;
    private static int filterProgram;
    private static int photoProgram;
    private static Texture pingTexture;

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
    private static int uniformTransform;
    private static int uniformOpacity;

    public static boolean hasFilters()
    {
        return isActive(BBSSettings.filmFilterBrightness, 0F)
            || isActive(BBSSettings.filmFilterContrast, 0F)
            || isActive(BBSSettings.filmFilterSaturation, 0F)
            || isActive(BBSSettings.filmFilterHue, 0F)
            || isActive(BBSSettings.filmFilterTemperature, 0F)
            || isActive(BBSSettings.filmFilterGamma, 1F)
            || isActive(BBSSettings.filmFilterSharpness, 0F)
            || isActive(BBSSettings.filmFilterVignette, 0F)
            || isActive(BBSSettings.filmFilterSepia, 0F);
    }

    public static boolean hasPhoto()
    {
        return getPhotoLink() != null;
    }

    public static Link getPhotoLink()
    {
        String texture = BBSSettings.filmPhotoTexture == null ? "" : BBSSettings.filmPhotoTexture.get();

        return texture == null || texture.isEmpty() ? null : Link.create(texture);
    }

    /**
     * The overlay photo loaded with linear filtering (a stretched photo shouldn't
     * turn blocky), or {@code null} when no photo was picked.
     */
    public static Texture getPhotoTexture()
    {
        Link link = getPhotoLink();

        return link == null ? null : BBSModClient.getTextures().getTexture(link, GL11.GL_LINEAR);
    }

    private static boolean isActive(ValueFloat value, float neutral)
    {
        return value != null && Math.abs(value.get() - neutral) > NEUTRAL_EPSILON;
    }

    /**
     * Bake the active effects into the export framebuffer's texture. GL state this
     * touches is saved up front and restored in {@code finally}, so vanilla's state
     * cache never goes stale no matter how the passes end.
     */
    public static void apply(Framebuffer framebuffer, int width, int height)
    {
        boolean filters = hasFilters();
        boolean photo = hasPhoto();

        if (broken || (!filters && !photo) || width <= 0 || height <= 0)
        {
            return;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

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
                applyFilters(framebuffer, width, height);
            }

            if (photo)
            {
                applyPhoto(width, height);
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
     */
    private static void applyFilters(Framebuffer framebuffer, int width, int height)
    {
        ensureFilterProgram();
        ensurePingTexture(width, height);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.id);
        pingTexture.bind();
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

        RenderSystem.disableBlend();
        GL20.glUseProgram(filterProgram);
        GL20.glUniform2f(uniformTexel, 1F / width, 1F / height);
        GL20.glUniform1f(uniformBrightness, 1F + BBSSettings.filmFilterBrightness.get());
        GL20.glUniform1f(uniformContrast, 1F + BBSSettings.filmFilterContrast.get());
        GL20.glUniform1f(uniformSaturation, 1F + BBSSettings.filmFilterSaturation.get());
        GL20.glUniform1f(uniformHue, MathUtils.toRad(BBSSettings.filmFilterHue.get()));
        GL20.glUniform1f(uniformTemperature, BBSSettings.filmFilterTemperature.get() * TEMPERATURE_STRENGTH);
        GL20.glUniform1f(uniformGamma, 1F / BBSSettings.filmFilterGamma.get());
        GL20.glUniform1f(uniformSharpness, BBSSettings.filmFilterSharpness.get() * SHARPNESS_STRENGTH);
        GL20.glUniform1f(uniformVignette, BBSSettings.filmFilterVignette.get());
        GL20.glUniform1f(uniformSepia, BBSSettings.filmFilterSepia.get());
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }

    /**
     * Photo pass, alpha-blended over the graded frame. The quad is placed in NDC:
     * scale 1 spans the frame's full height, the width keeps the photo's aspect
     * ratio, and the stretches multiply each axis on top of that.
     */
    private static void applyPhoto(int width, int height)
    {
        Texture photo = getPhotoTexture();

        if (photo == null || photo.width <= 0 || photo.height <= 0)
        {
            return;
        }

        ensurePhotoProgram();

        float scale = BBSSettings.filmPhotoScale.get();
        float halfW = scale * BBSSettings.filmPhotoStretchX.get() * (photo.width / (float) photo.height) * (height / (float) width);
        float halfH = scale * BBSSettings.filmPhotoStretchY.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GL20.glUseProgram(photoProgram);

        /* The setting's positive Y means "down the screen", NDC's positive Y is up */
        GL20.glUniform4f(uniformTransform, BBSSettings.filmPhotoX.get(), -BBSSettings.filmPhotoY.get(), halfW, halfH);
        GL20.glUniform1f(uniformOpacity, BBSSettings.filmPhotoOpacity.get());
        photo.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
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
    }

    private static void ensurePhotoProgram()
    {
        if (photoProgram != 0)
        {
            return;
        }

        photoProgram = compileProgram(PHOTO_VERTEX, PHOTO_FRAGMENT);
        uniformTransform = GL20.glGetUniformLocation(photoProgram, "u_transform");
        uniformOpacity = GL20.glGetUniformLocation(photoProgram, "u_opacity");
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

        /* The only sampler always reads from texture unit 0 */
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
}
