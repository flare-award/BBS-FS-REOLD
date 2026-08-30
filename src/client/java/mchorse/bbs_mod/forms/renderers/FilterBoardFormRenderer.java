package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FilmEffects;
import mchorse.bbs_mod.forms.forms.FilterBoardForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.joml.Vectors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Renders a billboard as a screen-space filter lens. The inherited billboard
 * path remains in charge of palette thumbnails and picking stencils; the world
 * path only supplies the projected quad and lets FilmEffects draw the snapshot.
 */
public class FilterBoardFormRenderer extends BillboardFormRenderer<FilterBoardForm>
{
    private final Quad quad = new Quad();
    private final Quad maskUV = new Quad();

    public FilterBoardFormRenderer(FilterBoardForm form)
    {
        super(form);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        /* The form editor's preview has no world framebuffer. Keep the stencil pass
         * pickable, but leave the visual viewport to the real model behind it. */
        if (context.type == FormRenderType.PREVIEW && !context.isPicking())
        {
            return;
        }

        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        if (context.isPicking() || !BBSRendering.isRenderingWorld())
        {
            super.render3D(context);

            return;
        }

        Link link = this.form.texture.get();

        if (link == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(link);

        if (texture == null || texture.width <= 0 || texture.height <= 0)
        {
            return;
        }

        this.buildGeometry(texture);

        Matrix4f model = new Matrix4f(context.stack.peek().getPositionMatrix());

        if (this.form.billboard.get())
        {
            Vector3f scale = Vectors.TEMP_3F;

            model.getScale(scale);
            model.m00(1).m01(0).m02(0);
            model.m10(0).m11(1).m12(0);
            model.m20(0).m21(0).m22(1);
            model.scale(scale);
        }

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(model);
        float[] positions = new float[24];
        float[] maskUV = new float[12];
        float[] filterUV = new float[12];

        /* Keep the same winding as BillboardFormRenderer. The custom shader
         * does not need normals, and culling is disabled for the back side. */
        this.putVertex(positions, maskUV, filterUV, 0, projection, this.quad.p3, this.maskUV.p3, 0F, 1F);
        this.putVertex(positions, maskUV, filterUV, 1, projection, this.quad.p2, this.maskUV.p2, 1F, 0F);
        this.putVertex(positions, maskUV, filterUV, 2, projection, this.quad.p1, this.maskUV.p1, 0F, 0F);
        this.putVertex(positions, maskUV, filterUV, 3, projection, this.quad.p3, this.maskUV.p3, 0F, 1F);
        this.putVertex(positions, maskUV, filterUV, 4, projection, this.quad.p4, this.maskUV.p4, 1F, 1F);
        this.putVertex(positions, maskUV, filterUV, 5, projection, this.quad.p2, this.maskUV.p2, 1F, 0F);

        FilmEffects.renderFilterBoard(this.form, texture, positions, maskUV, filterUV,
            this.form.color.get().a, this.form.linear.get(), this.form.mipmap.get());
    }

    private void buildGeometry(Texture texture)
    {
        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;
        Vector4f crop = this.form.crop.get();
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1F - crop.z / w;
        float uvBRy = 1F - crop.w / h;

        this.maskUV.p1.set(uvTLx, uvTLy, 0F);
        this.maskUV.p2.set(uvBRx, uvTLy, 0F);
        this.maskUV.p3.set(uvTLx, uvBRy, 0F);
        this.maskUV.p4.set(uvBRx, uvBRy, 0F);

        float uvFinalTLx = uvTLx;
        float uvFinalTLy = uvTLy;
        float uvFinalBRx = uvBRx;
        float uvFinalBRy = uvBRy;

        if (this.form.resizeCrop.get())
        {
            uvFinalTLx = 0F;
            uvFinalTLy = 0F;
            uvFinalBRx = 1F;
            uvFinalBRy = 1F;
            w -= crop.x + crop.z;
            h -= crop.y + crop.w;
        }

        if (w <= 0F || h <= 0F)
        {
            this.quad.p1.set(0F, 0F, 0F);
            this.quad.p2.set(0F, 0F, 0F);
            this.quad.p3.set(0F, 0F, 0F);
            this.quad.p4.set(0F, 0F, 0F);

            return;
        }

        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvFinalTLx - 0.5F) * ratioY;
        float TLy = -(uvFinalTLy - 0.5F) * ratioX;
        float BRx = (uvFinalBRx - 0.5F) * ratioY;
        float BRy = -(uvFinalBRy - 0.5F) * ratioX;

        this.quad.p1.set(TLx, TLy, 0F);
        this.quad.p2.set(BRx, TLy, 0F);
        this.quad.p3.set(TLx, BRy, 0F);
        this.quad.p4.set(BRx, BRy, 0F);

        float offsetX = this.form.offsetX.get();
        float offsetY = this.form.offsetY.get();
        float rotation = this.form.rotation.get();

        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (ow - crop.z)) / 2F / ow;
            float centerY = (crop.y + (oh - crop.w)) / 2F / ow;
            Matrix4f transform = new Matrix4f()
                .translate(centerX, centerY, 0F)
                .rotateZ(MathUtils.toRad(rotation))
                .translate(offsetX / ow, offsetY / oh, 0F)
                .translate(-centerX, -centerY, 0F);

            this.maskUV.transform(transform);
        }
    }

    private void putVertex(float[] positions, float[] maskUV, float[] filterUV, int index, Matrix4f projection, Vector3f point, Vector3f uv, float filterX, float filterY)
    {
        Vector4f clip = projection.transform(new Vector4f(point.x, point.y, point.z, 1F));
        int positionIndex = index * 4;
        int uvIndex = index * 2;

        positions[positionIndex] = clip.x;
        positions[positionIndex + 1] = clip.y;
        positions[positionIndex + 2] = clip.z;
        positions[positionIndex + 3] = clip.w;
        maskUV[uvIndex] = uv.x;
        maskUV[uvIndex + 1] = uv.y;
        filterUV[uvIndex] = filterX;
        filterUV[uvIndex + 1] = filterY;
    }
}
