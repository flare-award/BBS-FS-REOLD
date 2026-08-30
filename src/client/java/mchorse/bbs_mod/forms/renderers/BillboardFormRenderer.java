package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.FormMaterials;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Supplier;

public class BillboardFormRenderer <T extends BillboardForm> extends FormRenderer<T>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private static final Matrix4f matrix = new Matrix4f();

    public BillboardFormRenderer(T form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 1.5F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;

        this.renderModel(format, GameRenderer::getRenderTypeEntityTranslucentProgram,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition(), false
        );

        stack.pop();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
        int renderLayer = this.form.renderLayer.get();
        boolean forcedOpaque = renderLayer == Form.LAYER_SOLID || renderLayer == Form.LAYER_CUTOUT;

        /* The Material tab's Layer option: Solid and Cutout draw the quad immediately
         * with an alpha-tested program instead of deferring it as translucent. */
        Supplier<ShaderProgram> mainShader = shading
            ? (forcedOpaque
                ? (renderLayer == Form.LAYER_SOLID
                    ? GameRenderer::getRenderTypeEntitySolidProgram
                    : GameRenderer::getRenderTypeEntityCutoutProgram)
                : GameRenderer::getRenderTypeEntityTranslucentProgram)
            : GameRenderer::getPositionTexLightmapColorProgram;
        Supplier<ShaderProgram> shader = this.getShader(context,
            mainShader,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(format, shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    private void renderModel(VertexFormat format, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Link t = this.form.texture.get();

        if (t == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(t);

        /* Feed the Material tab's PBR sliders to the shader pack; the quad binds a
         * copy processed with the relief emboss and color overlay when set. */
        FormMaterials.update(t, this.form);

        Texture bound = FormMaterials.getProcessed(t, texture, this.form);

        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = this.form.crop.get();
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        float uvFinalTLx = uvTLx;
        float uvFinalTLy = uvTLy;
        float uvFinalBRx = uvBRx;
        float uvFinalBRy = uvBRy;

        if (this.form.resizeCrop.get())
        {
            uvFinalTLx = uvFinalTLy = 0F;
            uvFinalBRx = uvFinalBRy = 1F;

            w = w - crop.x - crop.z;
            h = h - crop.y - crop.w;
        }

        /* Calculate quad's size (vertices, not UV) */
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvFinalTLx - 0.5F) * ratioY;
        float TLy = -(uvFinalTLy - 0.5F) * ratioX;
        float BRx = (uvFinalBRx - 0.5F) * ratioY;
        float BRy = -(uvFinalBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        float offsetX = this.form.offsetX.get();
        float offsetY = this.form.offsetY.get();
        float rotation = this.form.rotation.get();

        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (ow - crop.z)) / 2F / ow;
            float centerY = (crop.y + (oh - crop.w)) / 2F / ow;

            matrix.identity()
                .translate(centerX, centerY, 0)
                .rotateZ(MathUtils.toRad(rotation))
                .translate(offsetX / ow, offsetY / oh, 0)
                .translate(-centerX, -centerY, 0);

            uvQuad.transform(matrix);
        }

        this.renderQuad(format, bound, shader, matrices, overlay, light, overlayColor, transition, defer);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Color color = new Color().set(overlayColor, true);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            Vector3f scale = Vectors.TEMP_3F;

            modelMatrix.getScale(scale);

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            modelMatrix.scale(scale);

            matrices.peek().getNormalMatrix().identity();
        }

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        ShaderProgram finalShader = shader.get();

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(() -> finalShader);

        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());
        builder.begin(VertexFormat.DrawMode.TRIANGLES, format);

        /* Front */
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, 1F).next();
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, 1F).next();
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, normal, 1F).next();

        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, 1F).next();
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, normal, 1F).next();
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, 1F).next();

        /* Back */
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, normal, -1F).next();
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, -1F).next();
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, -1F).next();

        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, -1F).next();
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, normal, -1F).next();
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, -1F).next();

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        boolean linear = this.form.linear.get();
        boolean mipmap = this.form.mipmap.get();
        int renderLayer = this.form.renderLayer.get();
        boolean forcedOpaque = renderLayer == Form.LAYER_SOLID || renderLayer == Form.LAYER_CUTOUT;
        boolean translucent = !forcedOpaque && (texture.hasTranslucency() || color.a < 1F || linear || mipmap);

        if (defer && translucent && FormTranslucentQueue.isActive())
        {
            /* The whole quad defers to the end-of-frame translucent pass: depth testing still
             * hides it behind opaque geometry, while the far-to-near sort blends it correctly
             * against other translucent forms — and it never occludes models behind it.
             * Linear/mipmap sampling manufactures fractional alpha at the edges even from a
             * binary-alpha texture, hence those flags count as translucency. */
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            buffer.upload(builder.end());
            VertexBuffer.unbind();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));
            Vector3f planeNormal = FormTranslucentQueue.quadPlaneNormal(modelView, matrix);

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                buffer, () -> finalShader, texture, modelView, null, origin, planeNormal, true,
                () ->
                {
                    texture.bind();
                    texture.setFilterMipmap(linear, mipmap);
                },
                () -> texture.setFilterMipmap(false, false)
            ));
        }
        else
        {
            /* Solid and Cutout draw with blending off: semi-transparent texels
             * go solid, fully transparent ones are cut by the program's alpha test */
            if (forcedOpaque)
            {
                RenderSystem.disableBlend();
            }

            BufferRenderer.drawWithGlobalProgram(builder.end());

            if (forcedOpaque)
            {
                RenderSystem.enableBlend();
            }
        }

        texture.setFilterMipmap(false, false);

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, Matrix3f normal, float nz)
    {
        if (format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR)
        {
            return consumer.vertex(matrix, x, y, 0F).texture(u, v).light(light).color(color.r, color.g, color.b, color.a);
        }

        return consumer.vertex(matrix, x, y, 0F).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(overlay).light(light).normal(normal, 0F, 0F, nz);
    }
}
