package mchorse.bbs_mod.ui.framework.elements.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.PixelArt;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.Supplier;

public class Batcher2D
{
    /** How far a lit edge is pulled towards white, see {@link #surfaceBox}. */
    private static final float HIGHLIGHT_STRENGTH = 0.15F;

    private static FontRenderer fontRenderer = new FontRenderer();

    private DrawContext context;
    private FontRenderer font;

    public static FontRenderer getDefaultTextRenderer()
    {
        fontRenderer.setRenderer(MinecraftClient.getInstance().textRenderer);

        return fontRenderer;
    }

    /**
     * Program for textured UI quads. The pixel art one keeps the seam between
     * texels even when the interface is drawn at a fractional scale, and falls
     * back to vanilla's when it's turned off or failed to compile.
     */
    private static Supplier<ShaderProgram> texturedProgram()
    {
        if (PixelArt.isEnabled() && BBSShaders.getPixelArtProgram() != null)
        {
            return BBSShaders::getPixelArtProgram;
        }

        return GameRenderer::getPositionTexColorProgram;
    }

    /**
     * Same, but a texture the user asked to be filtered linearly or mipmapped
     * (the toggles in the texture picker) keeps GL's own filtering — the pixel
     * art shader reads texels of level 0 directly, which would both render
     * those toggles meaningless and lean on a complete mipmap pyramid.
     */
    private static Supplier<ShaderProgram> texturedProgram(Texture texture)
    {
        if (texture != null && (texture.isLinear() || texture.isMipmap()))
        {
            return GameRenderer::getPositionTexColorProgram;
        }

        return texturedProgram();
    }

    /* Quad batching. A scope opened with beginBatch() collects every solid quad (box, outline,
     * surfaceBox and friends all funnel into box) into one dedicated buffer and draws it once at
     * endBatch() - instead of a begin/setShader/draw/flush per rectangle. Order stays exact
     * because only homogeneous solid quads batch: every other primitive (textures, text, clip)
     * flushes the pending quads first. The buffer is our own, not the shared Tessellator one,
     * so code that builds on the Tessellator directly can never collide with an open batch. */
    private BufferBuilder batchBuilder;
    private boolean batching;
    private boolean batchStarted;

    public Batcher2D(DrawContext context)
    {
        this.context = context;
        this.font = getDefaultTextRenderer();
    }

    public boolean isBatching()
    {
        return this.batching;
    }

    /** Open a quad batch. Nested calls are folded into the outermost scope. */
    public void beginBatch()
    {
        this.batching = true;
    }

    /** Close the scope opened by {@link #beginBatch()} and draw the collected quads. */
    public void endBatch()
    {
        this.batching = false;
        this.flushBatch();
    }

    private void flushBatch()
    {
        if (!this.batchStarted)
        {
            return;
        }

        this.batchStarted = false;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(this.batchBuilder.end());

        this.context.draw();
    }

    public DrawContext getContext()
    {
        return this.context;
    }

    public FontRenderer getFont()
    {
        return this.font;
    }

    /**
     * Swap the font every text call of this batcher goes through, handing back the
     * previous one so the caller can put it back. A null restores the default one.
     */
    public FontRenderer setFont(FontRenderer font)
    {
        FontRenderer previous = this.font;

        this.font = font == null ? getDefaultTextRenderer() : font;

        return previous;
    }

    /* Screen space clipping */

    public void clip(Area area, UIContext context)
    {
        this.clip(area.x, area.y, area.w, area.h, context);
    }

    /**
     * Clip to a rectangle given by its corners, matching how {@link #box} is called. The size-based
     * {@link #clip} right below reads almost identically at the call site, and passing corners to it
     * silently widens the region instead of failing.
     */
    public void clipBox(int x1, int y1, int x2, int y2, UIContext context)
    {
        this.clip(x1, y1, x2 - x1, y2 - y1, context);
    }

    public void clip(int x, int y, int w, int h, UIContext context)
    {
        this.clip(context.globalX(x), context.globalY(y), w, h, context.menu.width, context.menu.height);
    }

    /**
     * Scissor (clip) the screen
     */
    public void clip(int x, int y, int w, int h, int sw, int sh)
    {
        this.flushBatch();
        this.context.enableScissor(x, y, x + w, y + h);
    }

    public void unclip(UIContext context)
    {
        this.unclip(context.menu.width, context.menu.height);
    }

    public void unclip(int sw, int sh)
    {
        this.flushBatch();
        this.context.disableScissor();
    }

    /* Solid rectangles */

    public void normalizedBox(float x1, float y1, float x2, float y2, int color)
    {
        float temp = x1;

        x1 = Math.min(x1, x2);
        x2 = Math.max(temp, x2);

        temp = y1;

        y1 = Math.min(y1, y2);
        y2 = Math.max(temp, y2);

        this.box(x1, y1, x2, y2, color);
    }

    public void box(float x1, float y1, float x2, float y2, int color)
    {
        /* Under the custom gradient background, flat surface fills flow across
         * the screen: every box picks its corner colors from where it sits, so
         * the whole interface reads as one gradient assembled from its parts. */
        if (BBSSettings.isBackgroundGradient())
        {
            int end = BBSSettings.backgroundGradientEnd(color);

            if (end != 0)
            {
                float w = Math.max(1, this.context.getScaledWindowWidth());
                float h = Math.max(1, this.context.getScaledWindowHeight());

                this.box(x1, y1, x2 - x1, y2 - y1,
                    this.backgroundCorner(color, end, x1 / w, y1 / h),
                    this.backgroundCorner(color, end, x2 / w, y1 / h),
                    this.backgroundCorner(color, end, x1 / w, y2 / h),
                    this.backgroundCorner(color, end, x2 / w, y2 / h));

                return;
            }
        }

        this.box(x1, y1, x2 - x1, y2 - y1, color, color, color, color);
    }

    private int backgroundCorner(int start, int end, float tx, float ty)
    {
        int direction = BBSSettings.backgroundGradientDirection();
        float t = direction == BBSSettings.GRADIENT_HORIZONTAL ? tx
            : direction == BBSSettings.GRADIENT_VERTICAL ? ty
            : (tx + ty) * 0.5F;

        return Colors.lerp(start, end, MathUtils.clamp(t, 0F, 1F));
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();

        /* The matrix bakes into the vertices right here, so quads from different matrix
         * contexts share one batch safely. */
        if (this.batching)
        {
            if (!this.batchStarted)
            {
                if (this.batchBuilder == null)
                {
                    this.batchBuilder = new BufferBuilder(262144);
                }

                this.batchBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                this.batchStarted = true;
            }

            this.fillRect(this.batchBuilder, matrix4f, x, y, w, h, color1, color2, color3, color4);

            return;
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        this.fillRect(builder, matrix4f, x, y, w, h, color1, color2, color3, color4);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        this.context.draw();
    }

    /**
     * A rectangle cut along its top-left to bottom-right diagonal, a color to each half.
     * A color with an alpha channel is shown this way — its opaque half beside its real one,
     * both over a checkboard — so how transparent it is reads at a glance.
     */
    public void splitBox(float x1, float y1, float x2, float y2, int topLeft, int bottomRight)
    {
        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        builder.vertex(matrix4f, x1, y1, 0F).color(topLeft).next();
        builder.vertex(matrix4f, x1, y2, 0F).color(topLeft).next();
        builder.vertex(matrix4f, x2, y1, 0F).color(topLeft).next();
        builder.vertex(matrix4f, x2, y1, 0F).color(bottomRight).next();
        builder.vertex(matrix4f, x1, y2, 0F).color(bottomRight).next();
        builder.vertex(matrix4f, x2, y2, 0F).color(bottomRight).next();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        this.context.draw();
    }

    public void fillRect(BufferBuilder builder, Matrix4f matrix4f, float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        /* c1 ---- c2
         * |        |
         * c3 ---- c4 */
        builder.vertex(matrix4f, x, y, 0).color(color1).next();
        builder.vertex(matrix4f, x, y + h, 0).color(color3).next();
        builder.vertex(matrix4f, x + w, y + h, 0).color(color4).next();
        builder.vertex(matrix4f, x + w, y, 0).color(color2).next();
    }

    /* Rounded boxes and their shadow/outline rings: the "corner rounding" setting. The
     * radii are per corner (top left, top right, bottom right, bottom left), so a corner
     * pressed against a screen edge can be left square while the others round. */

    /** How finely a smooth corner arc is sampled. */
    private static final int ROUNDED_ARC_SEGMENTS = 8;

    /** The step of the pixelated corner staircase, in the interface's scaled pixels. */
    private static final float ROUNDED_PIXEL_STEP = 2F;

    /** How finely the ring's outlines are sampled around their perimeter. */
    private static final int ROUNDED_RING_SAMPLES = 96;

    /** Scratch for the fill's outline; the fan reads it while the batch builds. */
    private static final float[][] ROUNDED_FILL_POINTS = new float[1024][2];

    /** Scratch for the ring's four corner points of a band segment. */
    private static final float[][] ROUNDED_RING_POINTS = new float[4][2];

    /**
     * Fill a rectangle with per-corner radii. Zero radii on every corner fall back to the
     * regular box, so callers can pass the setting's radii unconditionally. The fill follows
     * the interface gradient background the way {@link #box} does.
     */
    public void roundBox(float x1, float y1, float x2, float y2, float radiusTL, float radiusTR, float radiusBR, float radiusBL, int color)
    {
        if (radiusTL <= 0F && radiusTR <= 0F && radiusBR <= 0F && radiusBL <= 0F)
        {
            this.box(x1, y1, x2, y2, color);

            return;
        }

        boolean pixel = BBSSettings.roundCorners != null && BBSSettings.roundCorners.get() == 2;

        if (BBSSettings.isBackgroundGradient())
        {
            int end = BBSSettings.backgroundGradientEnd(color);

            if (end != 0)
            {
                float w = Math.max(1, this.context.getScaledWindowWidth());
                float h = Math.max(1, this.context.getScaledWindowHeight());

                this.fillRounded(
                    this.backgroundCorner(color, end, x1 / w, y1 / h),
                    this.backgroundCorner(color, end, x2 / w, y1 / h),
                    this.backgroundCorner(color, end, x1 / w, y2 / h),
                    this.backgroundCorner(color, end, x2 / w, y2 / h),
                    x1, y1, x2, y2, radiusTL, radiusTR, radiusBR, radiusBL, pixel);

                return;
            }
        }

        this.fillRounded(color, color, color, color, x1, y1, x2, y2, radiusTL, radiusTR, radiusBR, radiusBL, pixel);
    }

    /**
     * A band between a rounded outline and the same shape expanded (or shrunk) by
     * {@code thickness}: the soft glow a floating panel casts, and its border, with rounded
     * corners. The colour fades from {@code innerColor} at the panel's outline to
     * {@code outerColor} at the far end of the band.
     */
    public void roundRing(float x1, float y1, float x2, float y2, float radiusTL, float radiusTR, float radiusBR, float radiusBL, float thickness, int innerColor, int outerColor, boolean outward)
    {
        if (thickness <= 0F)
        {
            return;
        }

        if (outward)
        {
            this.fillRoundedRing(
                x1, y1, x2, y2, radiusTL, radiusTR, radiusBR, radiusBL,
                x1 - thickness, y1 - thickness, x2 + thickness, y2 + thickness,
                radiusTL + thickness, radiusTR + thickness, radiusBR + thickness, radiusBL + thickness,
                innerColor, outerColor);
        }
        else
        {
            this.fillRoundedRing(
                x1, y1, x2, y2, radiusTL, radiusTR, radiusBR, radiusBL,
                x1 + thickness, y1 + thickness, x2 - thickness, y2 - thickness,
                Math.max(0F, radiusTL - thickness), Math.max(0F, radiusTR - thickness),
                Math.max(0F, radiusBR - thickness), Math.max(0F, radiusBL - thickness),
                innerColor, outerColor);
        }
    }

    /** The fill: a triangle fan over the rounded outline, one colour per corner blended bilinearly. */
    private void fillRounded(int colorTL, int colorTR, int colorBL, int colorBR, float x1, float y1, float x2, float y2, float rTL, float rTR, float rBR, float rBL, boolean pixel)
    {
        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int count = this.roundedOutline(x1, y1, x2, y2, rTL, rTR, rBR, rBL, pixel, ROUNDED_FILL_POINTS);

        float cx = (x1 + x2) * 0.5F;
        float cy = (y1 + y2) * 0.5F;
        int hubColor = this.bilinearColor(colorTL, colorTR, colorBL, colorBR, cx, cy, x1, y1, x2, y2);

        for (int i = 0; i < count; i ++)
        {
            float[] a = ROUNDED_FILL_POINTS[i];
            float[] b = ROUNDED_FILL_POINTS[(i + 1) % count];

            builder.vertex(matrix4f, cx, cy, 0F).color(hubColor).next();
            builder.vertex(matrix4f, a[0], a[1], 0F).color(this.bilinearColor(colorTL, colorTR, colorBL, colorBR, a[0], a[1], x1, y1, x2, y2)).next();
            builder.vertex(matrix4f, b[0], b[1], 0F).color(this.bilinearColor(colorTL, colorTR, colorBL, colorBR, b[0], b[1], x1, y1, x2, y2)).next();
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        this.context.draw();
    }

    /**
     * The ring: both outlines sampled at the same share of their perimeter, one quad between
     * each pair of neighbours, the inner rim at {@code innerColor} and the far one at
     * {@code outerColor}. Always smooth - it is a glow and a hairline, not a corner.
     */
    private void fillRoundedRing(float x1, float y1, float x2, float y2, float rTL, float rTR, float rBR, float rBL,
                                 float o1, float o2, float o3, float o4, float roTL, float roTR, float roBR, float roBL,
                                 int innerColor, int outerColor)
    {
        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < ROUNDED_RING_SAMPLES; i ++)
        {
            float t1 = (float) i / ROUNDED_RING_SAMPLES;
            float t2 = (float) (i + 1) / ROUNDED_RING_SAMPLES;

            this.roundedPoint(x1, y1, x2, y2, rTL, rTR, rBR, rBL, t1, ROUNDED_RING_POINTS[0]);
            this.roundedPoint(x1, y1, x2, y2, rTL, rTR, rBR, rBL, t2, ROUNDED_RING_POINTS[1]);
            this.roundedPoint(o1, o2, o3, o4, roTL, roTR, roBR, roBL, t1, ROUNDED_RING_POINTS[2]);
            this.roundedPoint(o1, o2, o3, o4, roTL, roTR, roBR, roBL, t2, ROUNDED_RING_POINTS[3]);

            float[] aIn = ROUNDED_RING_POINTS[0];
            float[] bIn = ROUNDED_RING_POINTS[1];
            float[] aOut = ROUNDED_RING_POINTS[2];
            float[] bOut = ROUNDED_RING_POINTS[3];

            builder.vertex(matrix4f, aIn[0], aIn[1], 0F).color(innerColor).next();
            builder.vertex(matrix4f, bIn[0], bIn[1], 0F).color(innerColor).next();
            builder.vertex(matrix4f, bOut[0], bOut[1], 0F).color(outerColor).next();
            builder.vertex(matrix4f, aOut[0], aOut[1], 0F).color(outerColor).next();
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        this.context.draw();
    }

    /** A colour of the rectangle's corners bilinearly blended at {@code (x, y)}. */
    private int bilinearColor(int colorTL, int colorTR, int colorBL, int colorBR, float x, float y, float x1, float y1, float x2, float y2)
    {
        float tx = MathUtils.clamp((x - x1) / Math.max(1F, x2 - x1), 0F, 1F);
        float ty = MathUtils.clamp((y - y1) / Math.max(1F, y2 - y1), 0F, 1F);

        return Colors.lerp(Colors.lerp(colorTL, colorTR, tx), Colors.lerp(colorBL, colorBR, tx), ty);
    }

    /**
     * The rounded outline, clockwise from the top edge's start, into {@code out} - the count
     * of written points comes back. The pixel style replaces each arc with a staircase of
     * axis-aligned steps, a Minecraft corner.
     */
    private int roundedOutline(float x1, float y1, float x2, float y2, float rTL, float rTR, float rBR, float rBL, boolean pixel, float[][] out)
    {
        int i = 0;

        /* The top edge, up to the top right corner's start */
        out[i][0] = x1 + rTL;
        out[i][1] = y1;
        i ++;

        /* Top right, then down the right edge to the bottom right */
        i = this.roundedCorner(out, i, x2 - rTR, y1 + rTR, rTR, 0, pixel, true);
        i = this.roundedCorner(out, i, x2 - rBR, y2 - rBR, rBR, 1, pixel, true);
        /* Bottom left, then up the left edge to the top left - whose end is the outline's
         * start, so its last point is left out to close the loop */
        i = this.roundedCorner(out, i, x1 + rBL, y2 - rBL, rBL, 2, pixel, true);
        i = this.roundedCorner(out, i, x1 + rTL, y1 + rTL, rTL, 3, pixel, false);

        return i;
    }

    /**
     * One corner's arc (or its staircase), into {@code out} at {@code i}. In the corner's own
     * coordinates the arc runs between {@code (p, q) = (0, r)} and {@code (r, 0)}, where
     * {@code p} and {@code q} are the offsets from the arc's centre along its two edges;
     * {@code (cx, cy)} is that centre and {@code corner} picks how the offsets map back to
     * the screen, so the four corners share one loop. Top right and bottom left walk from
     * {@code (0, r)} to {@code (r, 0)}; bottom right and top left walk the other way.
     */
    private int roundedCorner(float[][] out, int i, float cx, float cy, float r, int corner, boolean pixel, boolean includeEnd)
    {
        if (r <= 0F)
        {
            return i;
        }

        boolean back = corner == 1 || corner == 3;

        float p = back ? r : 0F;
        float q = back ? 0F : r;

        i = this.roundedCornerPoint(out, i, cx, cy, corner, p, q);

        if (pixel)
        {
            /* The staircase: a step of ROUNDED_PIXEL_STEP along one edge, then along the
             * other, until the corner is reached. Each point stays inside the arc, so the
             * steps never cut outside the rounded shape. */
            float step = ROUNDED_PIXEL_STEP;
            float pEnd = back ? 0F : r;
            float qEnd = back ? r : 0F;

            while (p != pEnd || q != qEnd)
            {
                float pNext = back ? Math.max(0F, p - step) : Math.min(r, p + step);
                float qNext = back ? Math.min(r, q + step) : Math.max(0F, q - step);

                if (pNext != p)
                {
                    i = this.roundedCornerPoint(out, i, cx, cy, corner, pNext, q);
                    p = pNext;
                }

                if (qNext != q)
                {
                    i = this.roundedCornerPoint(out, i, cx, cy, corner, p, qNext);
                    q = qNext;
                }
            }
        }
        else
        {
            int rows = ROUNDED_ARC_SEGMENTS;

            for (int k = 1; k <= rows; k ++)
            {
                float pk = back ? r * (1F - (float) k / rows) : r * (float) k / rows;
                float qk = (float) Math.sqrt(Math.max(0F, r * r - pk * pk));

                if (pk != p)
                {
                    i = this.roundedCornerPoint(out, i, cx, cy, corner, pk, q);
                }

                if (qk != q)
                {
                    i = this.roundedCornerPoint(out, i, cx, cy, corner, pk, qk);
                }

                p = pk;
                q = qk;
            }

            if (includeEnd)
            {
                float pe = back ? 0F : r;
                float qe = back ? r : 0F;

                if (pe != p)
                {
                    i = this.roundedCornerPoint(out, i, cx, cy, corner, pe, q);
                }

                if (qe != q)
                {
                    i = this.roundedCornerPoint(out, i, cx, cy, corner, pe, qe);
                }
            }
        }

        return i;
    }

    private int roundedCornerPoint(float[][] out, int i, float cx, float cy, int corner, float p, float q)
    {
        float x;
        float y;

        switch (corner)
        {
            case 0:
            {
                /* top right: out to the right, up from the centre */
                x = cx + p;
                y = cy - q;
                break;
            }
            case 1:
            {
                /* bottom right: out to the right, down */
                x = cx + p;
                y = cy + q;
                break;
            }
            case 2:
            {
                /* bottom left: out to the left, down */
                x = cx - p;
                y = cy + q;
                break;
            }
            default:
            {
                /* top left: out to the left, up */
                x = cx - p;
                y = cy - q;
            }
        }

        out[i][0] = x;
        out[i][1] = y;

        return i + 1;
    }

    /**
     * A point on the rounded outline at share {@code t} (0..1) of its perimeter, clockwise
     * from the top edge's start - the fill's walk, sampled instead of stepped.
     */
    private void roundedPoint(float x1, float y1, float x2, float y2, float rTL, float rTR, float rBR, float rBL, float t, float[] out)
    {
        float halfPi = MathUtils.PI * 0.5F;

        float top = Math.max(0F, x2 - x1 - rTL - rTR);
        float arcTR = halfPi * rTR;
        float right = Math.max(0F, y2 - y1 - rTR - rBR);
        float arcBR = halfPi * rBR;
        float bottom = Math.max(0F, x2 - x1 - rBR - rBL);
        float arcBL = halfPi * rBL;
        float left = Math.max(0F, y2 - y1 - rBL - rTL);
        float arcTL = halfPi * rTL;
        float total = top + arcTR + right + arcBR + bottom + arcBL + left + arcTL;

        if (total <= 0F)
        {
            out[0] = x1;
            out[1] = y1;

            return;
        }

        float d = MathUtils.clamp(t, 0F, 1F) * total;

        if (d < top)
        {
            out[0] = x1 + rTL + d;
            out[1] = y1;

            return;
        }

        d -= top;

        if (d < arcTR)
        {
            float a = -halfPi + (rTR > 0F ? d / rTR : 0F);

            out[0] = x2 - rTR + rTR * (float) Math.cos(a);
            out[1] = y1 + rTR + rTR * (float) Math.sin(a);

            return;
        }

        d -= arcTR;

        if (d < right)
        {
            out[0] = x2;
            out[1] = y1 + rTR + d;

            return;
        }

        d -= right;

        if (d < arcBR)
        {
            float a = rBR > 0F ? d / rBR : 0F;

            out[0] = x2 - rBR + rBR * (float) Math.cos(a);
            out[1] = y2 - rBR + rBR * (float) Math.sin(a);

            return;
        }

        d -= arcBR;

        if (d < bottom)
        {
            out[0] = x2 - rBR - d;
            out[1] = y2;

            return;
        }

        d -= bottom;

        if (d < arcBL)
        {
            float a = halfPi + (rBL > 0F ? d / rBL : 0F);

            out[0] = x1 + rBL + rBL * (float) Math.cos(a);
            out[1] = y2 - rBL + rBL * (float) Math.sin(a);

            return;
        }

        d -= arcBL;

        if (d < left)
        {
            out[0] = x1;
            out[1] = y2 - rBL - d;

            return;
        }

        d -= left;

        float a = MathUtils.PI + (rTL > 0F ? d / rTL : 0F);

        out[0] = x1 + rTL + rTL * (float) Math.cos(a);
        out[1] = y1 + rTL + rTL * (float) Math.sin(a);
    }

    public void surfaceBox(int x1, int y1, int x2, int y2, int fill, boolean shadow, boolean border)
    {
        if (border)
        {
            this.box(x1, y1, x2, y2, Colors.A100);

            x1++;
            y1++;
            x2--;
            y2--;
        }

        this.box(x1, y1, x2, y2, fill);

        /* Highlight and shadow are separate settings: the lit edges are the loud
         * half of the old bevel, so they're off by default and weaker than they
         * were — about six steps of the surface ramp instead of thirteen. */
        if (BBSSettings.interfaceHighlights.get())
        {
            int light = Colors.lerp(fill, Colors.WHITE, HIGHLIGHT_STRENGTH);

            this.box(x1, y1, x2, y1 + 1, light);
            this.box(x1, y1, x1 + 1, y2, light);
        }

        if (shadow && BBSSettings.interfaceShadows.get())
        {
            this.box(x1, y2 - 2, x2, y2, Colors.lerp(fill, Colors.A100, 0.4F));
        }
    }

    /**
     * A soft glow radiating out of a rectangle, with the rectangle itself filled
     * by the opaque colour. Every glow in the interface comes through here, so
     * the one toggle that turns them off is read here rather than at each
     * caller: every caller paints its own background over this rectangle right
     * after, which is what makes skipping the whole thing safe.
     */
    /**
     * {@link #surfaceBox(int, int, int, int, int, boolean, boolean)} whose fill flows
     * between two colors in the given direction, keeping the same bevel treatment.
     * The direction is one of the {@link BBSSettings} gradient constants: horizontal
     * flows left to right, vertical top to bottom, diagonal top-left to bottom-right.
     */
    public void gradientSurfaceBox(int x1, int y1, int x2, int y2, int startFill, int endFill, boolean shadow, boolean border, int direction)
    {
        if (border)
        {
            this.box(x1, y1, x2, y2, Colors.A100);

            x1++;
            y1++;
            x2--;
            y2--;
        }

        /* Corner colors, laid out as c1 (top-left), c2 (top-right), c3 (bottom-left),
         * c4 (bottom-right) - matching the box() vertex order */
        int c1 = startFill;
        int c2 = direction == BBSSettings.GRADIENT_VERTICAL ? startFill : endFill;
        int c3 = direction == BBSSettings.GRADIENT_HORIZONTAL ? startFill : endFill;
        int c4 = endFill;

        if (direction == BBSSettings.GRADIENT_DIAGONAL)
        {
            c2 = Colors.lerp(startFill, endFill, 0.5F);
            c3 = c2;
        }

        this.box(x1, y1, x2 - x1, y2 - y1, c1, c2, c3, c4);

        if (BBSSettings.interfaceHighlights.get())
        {
            int light1 = Colors.lerp(c1, Colors.WHITE, HIGHLIGHT_STRENGTH);
            int light2 = Colors.lerp(c2, Colors.WHITE, HIGHLIGHT_STRENGTH);
            int light3 = Colors.lerp(c3, Colors.WHITE, HIGHLIGHT_STRENGTH);

            this.box(x1, y1, x2 - x1, 1, light1, light2, light1, light2);
            this.box(x1, y1, 1, y2 - y1, light1, light1, light3, light3);
        }

        if (shadow && BBSSettings.interfaceShadows.get())
        {
            int dark3 = Colors.lerp(c3, Colors.A100, 0.4F);
            int dark4 = Colors.lerp(c4, Colors.A100, 0.4F);

            this.box(x1, y2 - 2, x2 - x1, 2, dark3, dark4, dark3, dark4);
        }
    }

    public void dropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        if (!BBSSettings.hasInterfaceGlow())
        {
            return;
        }

        this.flushBatch();

        left -= offset;
        top -= offset;
        right += offset;
        bottom += offset;

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* Draw opaque part */
        builder.vertex(matrix4f, left + offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f,left + offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, top + offset, 0).color(opaque).next();

        /* Draw top shadow */
        builder.vertex(matrix4f, left, top, 0).color(shadow).next();
        builder.vertex(matrix4f,left + offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right, top, 0).color(shadow).next();

        /* Draw bottom shadow */
        builder.vertex(matrix4f, left + offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f,left, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f, right, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f, right - offset, bottom - offset, 0).color(opaque).next();

        /* Draw left shadow */
        builder.vertex(matrix4f, left, top, 0).color(shadow).next();
        builder.vertex(matrix4f, left, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f, left + offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f,left + offset, top + offset, 0).color(opaque).next();

        /* Draw right shadow */
        builder.vertex(matrix4f, right - offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f,right, top, 0).color(shadow).next();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Gradients */

    /**
     * Draw a selection highlight over an area: a solid bar of the primary color along the
     * {@code edge}, fading into a gradient towards the opposite side. This is what marks the
     * chosen tab, mode or tool everywhere in the UI.
     */
    public void highlight(Area area, Direction edge)
    {
        int color = BBSSettings.primaryColor.get();

        /* Under the theme's gradient the highlight blends both accent colors,
         * so these edge bars follow the theme along with the buttons. */
        if (BBSSettings.isPrimaryGradient())
        {
            color = Colors.lerp(color, BBSSettings.primaryColorEnd(), 0.5F) & Colors.RGB;
        }

        this.highlight(area, edge, color);
    }

    /**
     * The same mark in a colour of its own — what a destructive button wears, so that "this one
     * is not like the others" is said the same way as "this one is the active one".
     */
    public void highlight(Area area, Direction edge, int color)
    {
        int bar = Colors.A100 | color;
        int near = Colors.A75 | color;
        int far = color;
        int t = 2;

        switch (edge)
        {
            case TOP:
                this.box(area.x, area.y, area.ex(), area.y + t, bar);
                this.gradientVBox(area.x, area.y + t, area.ex(), area.ey(), near, far);
                break;
            case BOTTOM:
                this.box(area.x, area.ey() - t, area.ex(), area.ey(), bar);
                this.gradientVBox(area.x, area.y, area.ex(), area.ey() - t, far, near);
                break;
            case LEFT:
                this.box(area.x, area.y, area.x + t, area.ey(), bar);
                this.gradientHBox(area.x + t, area.y, area.ex(), area.ey(), near, far);
                break;
            case RIGHT:
                this.box(area.ex() - t, area.y, area.ex(), area.ey(), bar);
                this.gradientHBox(area.x, area.y, area.ex() - t, area.ey(), far, near);
                break;
        }
    }

    /**
     * Fill with the user's accent color: flat primary normally, or the primary
     * gradient flowing in the configured direction when it's enabled. The alpha
     * mask (e.g. {@link Colors#A50}) applies to both ends, so accent highlights
     * all over the UI follow the theme the same way buttons do.
     */
    public void primaryBox(float x1, float y1, float x2, float y2, int alpha)
    {
        int start = (BBSSettings.primaryColor.get() & Colors.RGB) | alpha;

        if (!BBSSettings.isPrimaryGradient())
        {
            this.box(x1, y1, x2, y2, start);

            return;
        }

        int end = (BBSSettings.primaryColorEnd() & Colors.RGB) | alpha;
        int direction = BBSSettings.primaryGradientDirection();
        int c1 = start;
        int c2 = direction == BBSSettings.GRADIENT_VERTICAL ? start : end;
        int c3 = direction == BBSSettings.GRADIENT_HORIZONTAL ? start : end;
        int c4 = end;

        if (direction == BBSSettings.GRADIENT_DIAGONAL)
        {
            c2 = Colors.lerp(start, end, 0.5F);
            c3 = c2;
        }

        this.box(x1, y1, x2 - x1, y2 - y1, c1, c2, c3, c4);
    }

    public void gradientHBox(float x1, float y1, float x2, float y2, int leftColor, int rightColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, leftColor, rightColor, leftColor, rightColor);
    }

    public void gradientVBox(float x1, float y1, float x2, float y2, int topColor, int bottomColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, topColor, topColor, bottomColor, bottomColor);
    }

    public void dropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)
    {
        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y, 0F).color(opaque).next();

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (float) (x - Math.cos(a) * radius), (float) (y + Math.sin(a) * radius), 0F).color(shadow).next();
        }
    }

    public void dropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
        if (offset >= radius)
        {
            this.dropCircleShadow(x, y, radius, segments, opaque, shadow);

            return;
        }

        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        /* Draw opaque base */
        builder.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y, 0F).color(opaque).next();

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (int) (x - Math.cos(a) * offset), (int) (y + Math.sin(a) * offset), 0F).color(opaque).next();
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        /* Draw outer shadow */
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segments; i ++)
        {
            double alpha1 = i / (double) segments * Math.PI * 2 - Math.PI / 2;
            double alpha2 = (i + 1) / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (float) (x - Math.cos(alpha2) * offset), (float) (y + Math.sin(alpha2) * offset), 0F).color(opaque).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha1) * offset), (float) (y + Math.sin(alpha1) * offset), 0F).color(opaque).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha1) * radius), (float) (y + Math.sin(alpha1) * radius), 0F).color(shadow).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha2) * offset), (float) (y + Math.sin(alpha2) * offset), 0F).color(opaque).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha1) * radius), (float) (y + Math.sin(alpha1) * radius), 0F).color(shadow).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha2) * radius), (float) (y + Math.sin(alpha2) * radius), 0F).color(shadow).next();
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Outline methods */

    public void outlineCenter(float x, float y, float offset, int color)
    {
        this.outlineCenter(x, y, offset, color, 1);
    }

    public void outlineCenter(float x, float y, float offset, int color, int border)
    {
        this.outline(x - offset, y - offset, x + offset, y + offset, color, border);
    }

    public void outline(float x1, float y1, float x2, float y2, int color)
    {
        this.outline(x1, y1, x2, y2, color, 1);
    }

    /**
     * Draw rectangle outline with given border.
     */
    public void outline(float x1, float y1, float x2, float y2, int color, int border)
    {
        this.box(x1, y1, x1 + border, y2, color);
        this.box(x2 - border, y1, x2, y2, color);
        this.box(x1 + border, y1, x2 - border, y1 + border, color);
        this.box(x1 + border, y2 - border, x2 - border, y2, color);
    }

    /* Icon */

    /** In the light theme white foreground (text/icons) becomes black; other colours pass through. */
    private static int darkenWhite(int color)
    {
        return (color & 0xFFFFFF) == 0xFFFFFF ? (color & 0xFF000000) : color;
    }

    public void icon(Icon icon, float x, float y)
    {
        this.icon(icon, Colors.WHITE, x, y);
    }

    public void icon(Icon icon, int color, float x, float y)
    {
        this.icon(icon, color, x, y, 0F, 0F);
    }

    public void icon(Icon icon, float x, float y, float ax, float ay)
    {
        this.icon(icon, Colors.WHITE, x, y, ax, ay);
    }

    public void icon(Icon icon, int color, float x, float y, float ax, float ay)
    {
        if (icon.texture == null)
        {
            return;
        }

        if (BBSSettings.lightSurfaces())
        {
            color = darkenWhite(color);
        }

        x -= icon.w * ax;
        y -= icon.h * ay;

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, icon.w, icon.h, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    /**
     * An icon scaled to a square of {@code size}, for the few places where an icon stands in
     * for a picture and grows with its cell (a folder in a texture grid). Buttons never come
     * through here — their icons keep their own size.
     */
    public void scaledIcon(Icon icon, int color, float x, float y, float size)
    {
        if (icon.texture == null)
        {
            return;
        }

        if (BBSSettings.lightSurfaces())
        {
            color = darkenWhite(color);
        }

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, size, size, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    public void iconArea(Icon icon, float x, float y, float w, float h)
    {
        this.iconArea(icon, Colors.WHITE, x, y, w, h);
    }

    public void iconArea(Icon icon, int color, float x, float y, float w, float h)
    {
        if (BBSSettings.lightSurfaces())
        {
            color = darkenWhite(color);
        }

        this.texturedArea(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, w, h, icon.x, icon.y, icon.w, icon.h, icon.textureW, icon.textureH);
    }

    public void outlinedIcon(Icon icon, float x, float y, float ax, float ay)
    {
        this.outlinedIcon(icon, x, y, Colors.WHITE, ax, ay);
    }

    /**
     * Draw an icon with a black outline.
     */
    public void outlinedIcon(Icon icon, float x, float y, int color, float ax, float ay)
    {
        this.icon(icon, Colors.A100, x - 1, y, ax, ay);
        this.icon(icon, Colors.A100, x + 1, y, ax, ay);
        this.icon(icon, Colors.A100, x, y - 1, ax, ay);
        this.icon(icon, Colors.A100, x, y + 1, ax, ay);
        this.icon(icon, color, x, y, ax, ay);
    }

    /* Textured box */

    public void fullTexturedBox(Texture texture, float x, float y, float w, float h)
    {
        this.fullTexturedBox(texture, Colors.WHITE, x, y, w, h);
    }

    public void fullTexturedBox(Texture texture, int color, float x, float y, float w, float h)
    {
        this.texturedBox(texture, color, x, y, w, h, 0, 0, w, h, (int) w, (int) h);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2)
    {
        this.texturedBox(texture, color, x, y, w, h, u1, v1, u2, v2, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u, float v)
    {
        this.texturedBox(texture, color, x, y, w, h, u, v, u + w, v + h, texture.width, texture.height);
    }

    /**
     * A textured rectangle with per-corner radii - the picture's share of the corner
     * rounding, the way the banner follows the card it sits in. Zero radii fall back to the
     * regular textured box.
     */
    public void texturedRoundBox(Texture texture, int color, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, float rTL, float rTR, float rBR, float rBL)
    {
        if (rTL <= 0F && rTR <= 0F && rBR <= 0F && rBL <= 0F)
        {
            this.texturedBox(texture, color, x1, y1, x2 - x1, y2 - y1, u1, v1, u2, v2, texture.width, texture.height);

            return;
        }

        this.flushBatch();

        RenderSystem.setShaderTexture(0, texture.id);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.setShader(texturedProgram(texture));

        boolean pixel = BBSSettings.roundCorners != null && BBSSettings.roundCorners.get() == 2;
        int count = this.roundedOutline(x1, y1, x2, y2, rTL, rTR, rBR, rBL, pixel, ROUNDED_FILL_POINTS);

        float cx = (x1 + x2) * 0.5F;
        float cy = (y1 + y2) * 0.5F;
        float w = Math.max(1F, x2 - x1);
        float h = Math.max(1F, y2 - y1);
        float hubU = u1 + (cx - x1) / w * (u2 - u1);
        float hubV = v1 + (cy - y1) / h * (v2 - v1);

        for (int i = 0; i < count; i ++)
        {
            float[] a = ROUNDED_FILL_POINTS[i];
            float[] b = ROUNDED_FILL_POINTS[(i + 1) % count];

            builder.vertex(matrix, cx, cy, 0F).texture(hubU, hubV).color(color).next();
            builder.vertex(matrix, a[0], a[1], 0F).texture(u1 + (a[0] - x1) / w * (u2 - u1), v1 + (a[1] - y1) / h * (v2 - v1)).color(color).next();
            builder.vertex(matrix, b[0], b[1], 0F).texture(u1 + (b[0] - x1) / w * (u2 - u1), v1 + (b[1] - y1) / h * (v2 - v1)).color(color).next();
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.flushBatch();

        RenderSystem.setShaderTexture(0, texture.id);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        /* The colour carries an alpha and the caller means it, so blending is turned on here
         * rather than borrowed from whatever was drawn before - see the note above box() */
        RenderSystem.enableBlend();
        RenderSystem.setShader(texturedProgram(texture));

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        this.fillTexturedBox(builder, matrix, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void texturedBox(int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.texturedBox(texturedProgram(), texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    public void texturedBox(Supplier<ShaderProgram> shader, int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.flushBatch();

        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.setShader(shader);

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        this.fillTexturedBox(builder, matrix, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private void fillTexturedBox(BufferBuilder builder, Matrix4f matrix, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        builder.vertex(matrix, x, y + h, 0F).texture(u1 / (float) textureW, v2 / (float) textureH).color(color).next();
        builder.vertex(matrix, x + w, y + h, 0F).texture(u2 / (float) textureW, v2 / (float) textureH).color(color).next();
        builder.vertex(matrix, x + w, y, 0F).texture(u2 / (float) textureW, v1 / (float) textureH).color(color).next();
        builder.vertex(matrix, x, y + h, 0F).texture(u1 / (float) textureW, v2 / (float) textureH).color(color).next();
        builder.vertex(matrix, x + w, y, 0F).texture(u2 / (float) textureW, v1 / (float) textureH).color(color).next();
        builder.vertex(matrix, x, y, 0F).texture(u1 / (float) textureW, v1 / (float) textureH).color(color).next();
    }

    /* Repeatable textured box */

    public void texturedArea(Texture texture, int color, float x, float y, float w, float h, float u, float v, float tileW, float tileH, int tw, int th)
    {
        this.flushBatch();

        int countX = (int) (((w - 1) / tileW) + 1);
        int countY = (int) (((h - 1) / tileH) + 1);
        float fillerX = w - (countX - 1) * tileW;
        float fillerY = h - (countY - 1) * tileH;

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.setShader(texturedProgram(texture));
        RenderSystem.setShaderTexture(0, texture.id);

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0, c = countX * countY; i < c; i ++)
        {
            float ix = i % countX;
            float iy = i / countX;
            float xx = x + ix * tileW;
            float yy = y + iy * tileH;
            float xw = ix == countX - 1 ? fillerX : tileW;
            float yh = iy == countY - 1 ? fillerY : tileH;

            this.fillTexturedBox(builder, matrix, color, xx, yy, xw, yh, u, v, u + xw, v + yh, tw, th);
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Text with default font */

    public void text(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, false);
    }

    public void text(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, false);
    }

    public void textShadow(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, true);
    }

    public void textShadow(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, true);
    }

    public void text(String label, float x, float y, int color, boolean shadow)
    {
        if (BBSSettings.lightSurfaces())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        this.drawTextDirect(label, x, y, color, shadow);
    }

    /** Actual text draw (theming is applied by the public text() before calling this). */
    private void drawTextDirect(String label, float x, float y, int color, boolean shadow)
    {
        this.flushBatch();

        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        this.context.drawText(this.font.getRenderer(), label, (int) x, (int) y, color, shadow);
        this.context.draw();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    /* Text helpers */

    public int wallText(String text, int x, int y, int color, int width)
    {
        return this.wallText(text, x, y, color, width, 12);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight)
    {
        return this.wallText(text, x, y, color, width, lineHeight, 0F, 0F);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay)
    {
        return wallText(text, x, y, color, width, lineHeight, ax, ay, true);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay, boolean shadow)
    {
        List<String> list = this.font.wrap(text, width);
        int h = (lineHeight * (list.size() - 1)) + this.font.getHeight();

        y -= h * ay;

        for (String string : list)
        {
            this.text(string.toString(), (int) (x + (width - this.font.getWidth(string)) * ax), y, color, shadow);

            y += lineHeight;
        }

        return h;
    }

    public void textCard(String text, float x, float y)
    {
        this.textCard(text, x, y, Colors.WHITE, Colors.A50);
    }

    /**
     * In this context, text card is a text with some background behind it
     */
    public void textCard(String text, float x, float y, int color, int background)
    {
        this.textCard(text, x, y, color, background, 3);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset)
    {
        this.textCard(text, x, y, color, background, offset, true);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset, boolean shadow)
    {
        int a = background >> 24 & 0xff;

        if (a != 0)
        {
            if (BBSSettings.lightSurfaces() && (background & 0xFFFFFF) == 0)
            {
                background = (background & 0xFF000000) | 0xFFFFFF;
            }

            this.box(x - offset, y - offset, x + this.font.getWidth(text) + offset - 1, y + this.font.getHeight() + offset, background);
        }

        this.text(text, x, y, color, shadow);
    }

    public void flush()
    {
        this.flushBatch();
        this.context.draw();
    }
}