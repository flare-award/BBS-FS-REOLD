package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FilmEffects;
import mchorse.bbs_mod.forms.forms.FilterBoardForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.joml.Vectors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Renders a billboard-shaped world lens. Its plane uses the standard form
 * transform and a built-in white mask; no billboard image is drawn over the
 * filtered scene.
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

        Texture texture = BBSModClient.getTextures().getTexture(FilterBoardForm.MASK_TEXTURE);

        if (texture == null || texture.width <= 0 || texture.height <= 0)
        {
            return;
        }

        this.buildGeometry();

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

        FilmEffects.queueFilterBoard(this.form, texture, positions, maskUV, filterUV, 1F, false, false);
    }

    private void buildGeometry()
    {
        this.quad.p1.set(-0.5F, 0.5F, 0F);
        this.quad.p2.set(0.5F, 0.5F, 0F);
        this.quad.p3.set(-0.5F, -0.5F, 0F);
        this.quad.p4.set(0.5F, -0.5F, 0F);

        this.maskUV.p1.set(0F, 0F, 0F);
        this.maskUV.p2.set(1F, 0F, 0F);
        this.maskUV.p3.set(0F, 1F, 0F);
        this.maskUV.p4.set(1F, 1F, 0F);
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
