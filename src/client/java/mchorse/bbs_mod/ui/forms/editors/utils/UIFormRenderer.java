package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.api.client.events.FormPreviewEvents;
import mchorse.bbs_mod.client.FilmEffects;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class UIFormRenderer extends UIModelRenderer
{
    public Form form;

    /* The studio space: a dark far fill with a gridded plane under the model, in the spirit
     * of Blockbench's grid background. The plane is 32x32 around the origin, a fine line
     * every unit and a bold one every 8, so it reads as nine big cells like in Blockbench.
     * Both composite by depth: the fill at the far plane, the plane at its real place, so
     * the model stands in the floor and occludes it exactly where it is in front. */
    private static final Color STUDIO_BACKGROUND = new Color(0.102F, 0.114F, 0.141F);
    private static final Color STUDIO_PLANE = new Color(0.149F, 0.165F, 0.200F);
    private static final Color STUDIO_GRID_FINE = new Color(0.227F, 0.255F, 0.314F);
    private static final Color STUDIO_GRID_BOLD = new Color(0.361F, 0.400F, 0.471F);
    private static final float STUDIO_PLANE_HALF = 16F;

    /**
     * The depth the space fill sits at: just inside the far plane, so the model and the grid
     * (which wrote real depth in front of it) always occlude it. The fill is a quad at this
     * depth, not a color clear - a clear's result depends on the scissor and buffer state at
     * the moment, the quad composites by depth, so it can never land over the model.
     */
    private static final float SPACE_FAR_Z = 0.9999F;

    @Override
    protected void renderUserModelOverlay(UIContext context)
    {
        FormPreviewEvents.OVERLAY.invoker().render(this, context);
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, context.batcher.getContext().getMatrices(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        FormUtilsClient.render(this.form, formContext);
    }

    /**
     * The space this form's viewport shows around the model. Normal leaves whatever is under
     * the viewport in place; solid and studio fill it; photo lays the picture over it. The
     * fill sits at {@link #SPACE_FAR_Z} behind the model and the grid, so the compositing is
     * decided by the depth buffer, not by draw order. The values are the form's own, live - a
     * change in the space tab is visible on the next frame without any plumbing.
     */
    @Override
    protected void renderBackground(UIContext context)
    {
        Form form = this.form;

        if (form == null)
        {
            return;
        }

        int mode = MathUtils.clamp(form.spaceMode.get(), Form.SPACE_NORMAL, Form.SPACE_STUDIO);

        switch (mode)
        {
            case Form.SPACE_SOLID:
            {
                Color color = form.spaceColor.get();

                this.renderSpaceFill(context, color.r, color.g, color.b);

                break;
            }
            case Form.SPACE_PHOTO:
            {
                this.renderSpacePhoto(context, form);

                break;
            }
            case Form.SPACE_STUDIO:
            {
                this.renderSpaceStudio(context);

                break;
            }
            default:
            {
                /* Normal: the world under the viewport stays, exactly as before the space
                 * existed. */
            }
        }
    }

    /**
     * The flat space fill: a full-viewport quad at the far depth, in NDC. Depth is tested
     * (the viewport's depth was just cleared, so nothing is in front of it yet) and written,
     * so everything the pass draws afterwards - the grid, the model, its translucent parts -
     * composites over it by depth, and nothing drawn earlier can show through except what
     * the quad itself leaves alone.
     */
    private void renderSpaceFill(UIContext context, float r, float g, float b)
    {
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter previousSorter = RenderSystem.getVertexSorting();

        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorter.BY_Z);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        try
        {
            Matrix4f identity = new Matrix4f();
            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            builder.vertex(identity, -1F, -1F, SPACE_FAR_Z).color(r, g, b, 1F).next();
            builder.vertex(identity, -1F, 1F, SPACE_FAR_Z).color(r, g, b, 1F).next();
            builder.vertex(identity, 1F, 1F, SPACE_FAR_Z).color(r, g, b, 1F).next();
            builder.vertex(identity, 1F, -1F, SPACE_FAR_Z).color(r, g, b, 1F).next();

            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
        finally
        {
            RenderSystem.enableCull();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorter);
        }
    }

    /**
     * The photo behind the model: a screen-space quad in the same placement language as the
     * film's photo layers (scale 1 spans the viewport's full height, x/y roam +-2, the width
     * keeps the photo's aspect). It sits at the far depth with the depth test on, so the
     * model and its translucent parts draw over it. Where the quad does not reach, whatever
     * was under the viewport - the world - stays visible, on purpose.
     */
    private void renderSpacePhoto(UIContext context, Form form)
    {
        Texture photo = FilmEffects.getPhotoTexture(form.spacePhoto.get());

        if (photo == null || photo.width <= 0 || photo.height <= 0)
        {
            return;
        }

        float opacity = MathUtils.clamp(form.spacePhotoOpacity.get(), 0F, 1F);

        if (opacity <= 0F)
        {
            return;
        }

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

        /* The quad is in NDC, so the camera's projection matrix is swapped out for an
         * identity (the vertices carry their own identity model matrix) and restored
         * afterwards */
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorter.BY_Z);
        BBSModClient.getTextures().bindTexture(photo);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        try
        {
            this.drawSpacePhotoQuad(photo, opacity,
                form.spacePhotoX.get(), form.spacePhotoY.get(), form.spacePhotoScale.get(), form.spacePhotoRotate.get(),
                width, height);
        }
        finally
        {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorter);
        }
    }

    /** One photo quad in NDC at the far depth; the placement math is the film's, so both features agree. */
    private void drawSpacePhotoQuad(Texture photo, float opacity, float x, float y, float scale, float rotate, int width, int height)
    {
        float halfW = scale * (photo.width / (float) photo.height) * (height / (float) width);
        float halfH = scale;
        float angle = MathUtils.toRad(-rotate);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float aspect = width / (float) height;
        Matrix4f identity = new Matrix4f();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        /* Counterclockwise from the top left corner */
        float[] corners = {-1F, 1F, -1F, -1F, 1F, -1F, 1F, 1F};

        for (int i = 0; i < 4; i ++)
        {
            float cx = corners[i * 2];
            float cy = corners[i * 2 + 1];

            /* Rotate in frame space so the photo doesn't skew on wide screens */
            float px = cx * halfW * aspect;
            float py = cy * halfH;
            float rx = (px * cos - py * sin) / aspect;
            float ry = px * sin + py * cos;

            builder.vertex(identity, x + rx, -y + ry, SPACE_FAR_Z)
                .texture(cx * 0.5F + 0.5F, 0.5F - cy * 0.5F)
                .color(1F, 1F, 1F, opacity)
                .next();
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /**
     * The studio space: the dark far fill, then the plane with its grid in scene space. The
     * plane and the lines follow the scene matrix, so when a rotated model block is edited
     * immersively the floor turns with it, the way the grid does; real depth, so the model
     * stands in it and occludes it where it is in front.
     */
    private void renderSpaceStudio(UIContext context)
    {
        this.renderSpaceFill(context, STUDIO_BACKGROUND.r, STUDIO_BACKGROUND.g, STUDIO_BACKGROUND.b);

        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        try
        {
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            this.fillPlaneQuad(builder, matrix4f);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            this.fillGridLines(builder, matrix4f);
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
        finally
        {
            RenderSystem.enableCull();
        }
    }

    private void fillPlaneQuad(BufferBuilder builder, Matrix4f matrix4f)
    {
        float x = STUDIO_PLANE_HALF;
        Color color = STUDIO_PLANE;

        builder.vertex(matrix4f, -x, 0F, -x).color(color.r, color.g, color.b, 1F).next();
        builder.vertex(matrix4f, -x, 0F, x).color(color.r, color.g, color.b, 1F).next();
        builder.vertex(matrix4f, x, 0F, x).color(color.r, color.g, color.b, 1F).next();
        builder.vertex(matrix4f, x, 0F, -x).color(color.r, color.g, color.b, 1F).next();
    }

    private void fillGridLines(BufferBuilder builder, Matrix4f matrix4f)
    {
        for (int i = -16; i <= 16; i += 8)
        {
            this.gridLine(builder, matrix4f, i, -16, i, 16, STUDIO_GRID_BOLD);
            this.gridLine(builder, matrix4f, -16, i, 16, i, STUDIO_GRID_BOLD);
        }

        for (int i = -16; i <= 16; i ++)
        {
            if (i % 8 == 0)
            {
                continue;
            }

            this.gridLine(builder, matrix4f, i, -16, i, 16, STUDIO_GRID_FINE);
            this.gridLine(builder, matrix4f, -16, i, 16, i, STUDIO_GRID_FINE);
        }
    }

    private void gridLine(BufferBuilder builder, Matrix4f matrix4f, float x1, float z1, float x2, float z2, Color color)
    {
        builder.vertex(matrix4f, x1, 0F, z1).color(color.r, color.g, color.b, 1F).next();
        builder.vertex(matrix4f, x2, 0F, z2).color(color.r, color.g, color.b, 1F).next();
    }

    /**
     * In the studio the gridded plane is the floor, so the plain line grid would only double
     * it - skip it, keep it for every other space.
     */
    @Override
    protected void renderGrid(UIContext context)
    {
        if (this.form != null && MathUtils.clamp(this.form.spaceMode.get(), Form.SPACE_NORMAL, Form.SPACE_STUDIO) == Form.SPACE_STUDIO)
        {
            return;
        }

        super.renderGrid(context);
    }
}
