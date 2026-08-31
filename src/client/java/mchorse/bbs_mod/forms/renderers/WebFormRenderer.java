package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsWorldCollisions;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.WebForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders a web as a small procedural tube and simulates it in world space. The
 * form itself stays a normal body-part form: its local endpoints are transformed
 * by the current model/bone frame, while the solver can keep a captured anchor
 * in world space when the endpoint is locked.
 */
public class WebFormRenderer extends FormRenderer<WebForm>
{
    private static final int RING_SEGMENTS = 8;
    private static final float TWO_PI = (float) (Math.PI * 2D);

    /* The ring is the same every frame: its trigonometry and shading are tabulated once. */
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SHADE = new float[RING_SEGMENTS + 1];

    static
    {
        for (int i = 0; i <= RING_SEGMENTS; i++)
        {
            float angle = i / (float) RING_SEGMENTS * TWO_PI;

            RING_COS[i] = (float) Math.cos(angle);
            RING_SIN[i] = (float) Math.sin(angle);
            RING_SHADE[i] = 0.78F + 0.22F * (0.5F + 0.5F * (float) Math.cos(angle - 0.7F));
        }
    }
    private static final long MAX_TICK_STEP = 8L;

    /** Hard cap on solver steps in a single frame, so a hitch can't cascade. */
    private static final int MAX_STEPS_PER_FRAME = 8;
    private static final float MIN_DISTANCE = 1.0E-4F;
    private static final float GRAVITY_SCALE = 0.02F;
    private static final float WIND_SCALE = 0.02F;

    /** Furthest a simulated point may sit from the shooter before the rope is restarted. */
    private static final float MAX_POINT_DISTANCE = 256F;

    /** Furthest a simulated point may travel in one tick. */
    private static final float MAX_STEP = 2F;

    /** How quickly an attachment's orientation follows the rope (0 frozen, 1 instant). */
    private static final float ORIENT_SMOOTHING = 0.35F;

    /**
     * Mass of one bare rope point, in the same kilograms the body parts use. A load
     * is only felt relative to the rope itself, so this is what "heavy" is measured
     * against: a 60 kg rider on a 2 kg point moves the line, a 0.5 kg rat barely does.
     */
    private static final float POINT_MASS = 2F;

    /** Damping left at a fully loaded point - heavy things keep their momentum. */
    private static final float HEAVY_DAMPING_SCALE = 0.35F;

    /* Attachment point names offered to body parts */
    private static final String BONE_START = "start";
    private static final String BONE_MIDDLE = "middle";
    private static final String BONE_END = "end";
    private static final String BONE_POINT_PREFIX = "point_";
    private static final String FIXED_SUFFIX = "_fixed";

    private final Map<IEntity, PhysicsState> states = new WeakHashMap<>();
    private final PhysicsState nullEntityState = new PhysicsState();

    /**
     * The points of the last drawn web, in the same local space as the render stack.
     * Body parts attach to these, so a model can ride the tip of a swinging line.
     */
    private Vector3f[] attachPoints;

    /* Mesh scratch. A web is rebuilt every frame, so none of this may allocate. */
    private Vector3f[] localBuffer = new Vector3f[0];
    private Vector3f[] strandBuffer = new Vector3f[0];
    private Vector3f[] frameNormals = new Vector3f[0];
    private Vector3f[] frameBinormals = new Vector3f[0];
    private final Vector3f scratchTangent = new Vector3f();
    private final Vector3f scratchReference = new Vector3f();
    private final Vector3f scratchOrient = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchForward = new Vector3f();

    /** Low-passed rope direction per attachment point, keyed by its name. */
    private final Map<String, Vector3f> smoothedUp = new HashMap<>();

    /** Attached mass per rope point (kg), rebuilt every frame from the body parts. */
    private float[] pointMass = new float[0];
    private boolean loaded;
    private final Vector3f ringA = new Vector3f();
    private final Vector3f ringB = new Vector3f();
    private final Vector3f ringC = new Vector3f();
    private final Vector3f ringD = new Vector3f();

    public WebFormRenderer(WebForm form)
    {
        super(form);
    }

    /**
     * Attachment points offered to body parts. The web has no model, so the
     * "bones" are its own anchors: the shooter's end, the middle, the tip, and
     * every simulated point in between. The plain names orient the attached form
     * along the line (its Y axis points back up the web, so a model hangs from
     * it naturally); the {@code _fixed} ones only move it, leaving the rotation
     * to the body part's own transform.
     */
    @Override
    public List<String> getBones()
    {
        int segments = MathUtils.clamp(this.form.segments.get(), 2, 64);
        List<String> bones = new ArrayList<>();

        bones.add(BONE_START);
        bones.add(BONE_MIDDLE);
        bones.add(BONE_END);
        bones.add(BONE_START + FIXED_SUFFIX);
        bones.add(BONE_MIDDLE + FIXED_SUFFIX);
        bones.add(BONE_END + FIXED_SUFFIX);

        for (int i = 0; i < segments; i++)
        {
            /* Zero padded: the picker sorts the list alphabetically. */
            bones.add(String.format("%s%02d", BONE_POINT_PREFIX, i + 1));
        }

        return bones;
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Matrix4f attachment = this.getAttachmentMatrix(part.bone.get());

            if (attachment == null)
            {
                this.renderBodyPart(part, context);

                continue;
            }

            context.stack.push();
            MatrixStackUtils.multiply(context.stack, attachment);

            if (context.world != null)
            {
                context.world.push();
                MatrixStackUtils.multiply(context.world, attachment);
            }

            this.renderBodyPart(part, context);

            context.stack.pop();

            if (context.world != null)
            {
                context.world.pop();
            }
        }
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        Matrix4f mm = new Matrix4f();
        Matrix4f oo = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.peek().getPositionMatrix());

        matrices.put(prefix, mm, oo);

        int i = 0;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f attachment = this.getAttachmentMatrix(part.bone.get());

                stack.push();

                if (attachment != null)
                {
                    /* The editor's gizmo has to sit where the part is actually drawn. */
                    MatrixStackUtils.multiply(stack, attachment);
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();
    }

    /**
     * The local matrix of an attachment point, or null when the name isn't one of
     * ours (an empty bone, or a leftover name from another form - the part then
     * hangs off the web's origin exactly like before).
     */
    private Matrix4f getAttachmentMatrix(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        Vector3f[] points = this.attachPoints;

        if (points == null || points.length < 2)
        {
            return null;
        }

        boolean oriented = !bone.endsWith(FIXED_SUFFIX);
        String name = oriented ? bone : bone.substring(0, bone.length() - FIXED_SUFFIX.length());
        int index;

        if (name.equals(BONE_START))
        {
            index = 0;
        }
        else if (name.equals(BONE_END))
        {
            index = points.length - 1;
        }
        else if (name.equals(BONE_MIDDLE))
        {
            index = points.length / 2;
        }
        else if (name.startsWith(BONE_POINT_PREFIX))
        {
            try
            {
                index = Integer.parseInt(name.substring(BONE_POINT_PREFIX.length())) - 1;
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
        else
        {
            return null;
        }

        /* Fewer simulated points than the name asks for (the segment count changed
         * after the part was attached): clamp to the tip instead of dropping it. */
        index = MathUtils.clamp(index, 0, points.length - 1);

        Vector3f point = points[index];

        if (!oriented)
        {
            return new Matrix4f().translate(point);
        }

        Vector3f up = new Vector3f();

        if (index == 0)
        {
            up.set(points[0]).sub(points[1]);
        }
        else
        {
            up.set(points[index - 1]).sub(points[index]);
        }

        if (up.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
        {
            return new Matrix4f().translate(point);
        }

        up.normalize();

        /* Two ropes apart, one solver step apart, the raw direction jitters by a few
         * degrees every frame - which reads as the hanging model shivering. The
         * direction is low-passed per attachment point instead. */
        Vector3f smoothed = this.smoothedUp.computeIfAbsent(bone, (key) -> new Vector3f(up));

        smoothed.lerp(up, ORIENT_SMOOTHING);

        if (smoothed.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
        {
            smoothed.set(up);
        }
        else
        {
            smoothed.normalize();
        }

        return this.orientationMatrix(point, smoothed);
    }

    /**
     * A stable frame for a hanging form: Y along the rope, the other two axes pinned
     * to a fixed reference.
     *
     * <p>The previous version asked for the shortest arc from +Y to the rope
     * direction. That leaves the roll around the rope completely undetermined - as
     * the line swings, the shortest arc twists with it, which is exactly the "it
     * suddenly turns 45-90 degrees" the rider sees. Building the basis explicitly
     * from a reference axis gives the same orientation for the same direction, every
     * frame, with no accumulated or flipping roll.</p>
     */
    private Matrix4f orientationMatrix(Vector3f point, Vector3f up)
    {
        Vector3f reference = this.scratchOrient;

        /* Only swap the reference when the rope is almost parallel to it, so the
         * usual near-vertical rope always resolves the same way. */
        reference.set(Math.abs(up.z) < 0.9F ? 0F : 1F, 0F, Math.abs(up.z) < 0.9F ? 1F : 0F);

        /* right = up x reference, so a straight-up rope gives exactly the identity
         * basis and a form hung on it faces the way it does everywhere else. */
        Vector3f right = this.scratchRight.set(up).cross(reference);

        if (right.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
        {
            return new Matrix4f().translate(point);
        }

        right.normalize();

        Vector3f forward = this.scratchForward.set(right).cross(up).normalize();

        return new Matrix4f(
            right.x, right.y, right.z, 0F,
            up.x, up.y, up.z, 0F,
            forward.x, forward.y, forward.z, 0F,
            point.x, point.y, point.z, 1F
        );
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int width = x2 - x1;
        int height = y2 - y1;
        float left = x1 + Math.max(8F, width * 0.12F);
        float right = x2 - Math.max(8F, width * 0.12F);
        float top = y1 + Math.max(12F, height * 0.18F);
        float bottom = y2 - Math.max(12F, height * 0.18F);
        float sag = Math.min(height * 0.22F, Math.max(4F, height * 0.08F));
        Color color = this.form.color.get().copy();
        int mainColor = color.getARGBColor();
        int shadowColor = new Color(0F, 0F, 0F, Math.min(0.45F, color.a)).getARGBColor();
        float previousX = left;
        float previousY = bottom;
        int previewSegments = 18;

        for (int i = 1; i <= previewSegments; i++)
        {
            float progress = i / (float) previewSegments;
            float px = left + (right - left) * progress;
            float py = bottom + (top - bottom) * progress + (float) Math.sin(Math.PI * progress) * sag;

            this.drawPreviewSegment(context, previousX + 1F, previousY + 1F, px + 1F, py + 1F, 2F, shadowColor);
            this.drawPreviewSegment(context, previousX, previousY, px, py, Math.max(1F, Math.min(3F, this.form.thickness.get() * 40F)), mainColor);

            previousX = px;
            previousY = py;
        }

        context.batcher.outline(left - 2F, bottom - 2F, left + 3F, bottom + 3F, mainColor);
        context.batcher.outline(right - 2F, top - 2F, right + 3F, top + 3F, mainColor);
    }

    private void drawPreviewSegment(UIContext context, float x1, float y1, float x2, float y2, float thickness, int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);
        float cx = (x1 + x2) * 0.5F;
        float cy = (y1 + y2) * 0.5F;

        context.batcher.getContext().getMatrices().push();
        context.batcher.getContext().getMatrices().translate(cx, cy, 0F);
        context.batcher.getContext().getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(angle));
        context.batcher.box(-length * 0.5F, -thickness * 0.5F, length, thickness, color);
        context.batcher.getContext().getMatrices().pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        if (context.type == FormRenderType.ITEM_INVENTORY && !context.isPicking())
        {
            Vector3f[] inventoryPoints = this.buildInventoryPoints();

            this.attachPoints = inventoryPoints;
            this.renderPoints(context, inventoryPoints);

            return;
        }

        Matrix4f worldMatrix = this.getWorldMatrix(context);
        Matrix4f inverseWorld = this.getInverse(worldMatrix);
        Vector3f startWorld = this.transformPosition(worldMatrix, this.form.start.get());
        Vector3f endpointWorld = this.transformPosition(worldMatrix, this.form.end.get());
        PhysicsState state = this.getState(context.entity);
        int segments = MathUtils.clamp(this.form.segments.get(), 2, 64);
        int anchorMode = MathUtils.clamp(this.form.anchorMode.get(), WebForm.ANCHOR_FOLLOW, WebForm.ANCHOR_FREE);
        boolean simulate = this.form.physics.get() && context.type != FormRenderType.ITEM_INVENTORY && !context.ui;

        if (state.needsReset(segments, anchorMode, simulate, this.form.length.get(), this.form.sag.get()))
        {
            Vector3f resetEndpoint = anchorMode == WebForm.ANCHOR_LOCKED && state.anchorMode == WebForm.ANCHOR_LOCKED && state.points.length > 0
                ? new Vector3f(state.lockedEnd) : endpointWorld;

            state.reset(segments, anchorMode, simulate, startWorld, resetEndpoint, this.form.length.get(), this.form.sag.get());
        }

        Vector3f endWorld = endpointWorld;

        if (anchorMode == WebForm.ANCHOR_LOCKED)
        {
            endWorld = new Vector3f(state.lockedEnd);
        }

        Vector3f[] worldPoints;

        this.collectMasses(segments);

        if (simulate)
        {
            double time = this.getSimulationTime(context);

            this.updateSimulation(state, context, time, startWorld, endWorld);

            /* The solve runs at 20 steps per second like the rest of the game; the
             * frame in between reads an interpolated copy, so the rope moves at the
             * monitor's rate instead of visibly stepping at tick rate. */
            worldPoints = state.interpolate(startWorld, endWorld);
        }
        else
        {
            if (anchorMode == WebForm.ANCHOR_FREE && !simulate)
            {
                endWorld = endpointWorld;
            }
            else if (anchorMode == WebForm.ANCHOR_FREE)
            {
                endWorld = new Vector3f(state.initialEnd);
            }

            worldPoints = this.buildStaticPoints(startWorld, endWorld, segments, this.form.sag.get());
        }

        this.localBuffer = ensureVectors(this.localBuffer, worldPoints.length);

        Vector3f[] localPoints = this.localBuffer;

        for (int i = 0; i < worldPoints.length; i++)
        {
            inverseWorld.transformPosition(localPoints[i].set(worldPoints[i]));
        }

        /* Body parts hang off these, so they have to be the very points that were
         * drawn: same space as the render stack, same frame, physics included. */
        this.attachPoints = localPoints;

        this.renderPoints(context, localPoints);
    }

    private Vector3f[] buildInventoryPoints()
    {
        int segments = MathUtils.clamp(this.form.segments.get(), 2, 64);
        Vector3f start = new Vector3f(this.form.start.get());
        Vector3f end = new Vector3f(this.form.end.get());

        return this.buildStaticPoints(start, end, segments, this.form.sag.get());
    }

    private void renderPoints(FormRenderingContext context, Vector3f[] points)
    {
        if (points == null || points.length < 2)
        {
            return;
        }

        Matrix4f model = new Matrix4f(context.stack.peek().getPositionMatrix());
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Color color;

        if (context.isPicking())
        {
            color = this.getPickingColor(context.getPickingIndex());
        }
        else
        {
            color = new Color().set(context.color, true);
            FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

            if (BBSRendering.isIrisShadowPass())
            {
                color.a = 1F;
            }
        }

        float alpha = color.a;
        int renderLayer = this.form.renderLayer.get();
        boolean forcedOpaque = renderLayer == Form.LAYER_SOLID || renderLayer == Form.LAYER_CUTOUT;
        boolean translucent = !forcedOpaque && (renderLayer == Form.LAYER_TRANSLUCENT || alpha < 0.999F);
        boolean defer = translucent && !context.isPicking() && !context.ui && context.type != FormRenderType.ITEM_INVENTORY
            && FormTranslucentQueue.isActive();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        this.appendTube(builder, model, points, color);

        if (defer)
        {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            buffer.upload(builder.end());
            VertexBuffer.unbind();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Vector3f origin = modelView.transformPosition(model.getTranslation(new Vector3f()));

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                buffer, GameRenderer::getPositionColorProgram, null, modelView, null, origin, null, false, null, null
            ));

            return;
        }

        if (forcedOpaque || !translucent)
        {
            RenderSystem.disableBlend();
        }
        else
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        if (forcedOpaque || !translucent)
        {
            RenderSystem.enableBlend();
        }
    }

    private void appendTube(BufferBuilder builder, Matrix4f model, Vector3f[] center, Color color)
    {
        int strandCount = MathUtils.clamp(this.form.strands.get(), 1, 3);
        float thickness = Math.max(0.0005F, this.form.thickness.get() * 0.5F);
        float spread = Math.max(0F, this.form.strandSpread.get());

        for (int strand = 0; strand < strandCount; strand++)
        {
            Vector3f[] points = this.buildStrand(center, strand, strandCount, spread);
            float radius = thickness * (strandCount == 1 ? 1F : 0.72F);

            this.computeFrames(points);

            for (int i = 0; i < points.length - 1; i++)
            {
                Vector3f pointA = points[i];
                Vector3f pointB = points[i + 1];
                Vector3f normalA = this.frameNormals[i];
                Vector3f binormalA = this.frameBinormals[i];
                Vector3f normalB = this.frameNormals[i + 1];
                Vector3f binormalB = this.frameBinormals[i + 1];
                float progressA = i / (float) (points.length - 1);
                float progressB = (i + 1) / (float) (points.length - 1);
                float radiusA = radius * this.getTaper(progressA);
                float radiusB = radius * this.getTaper(progressB);

                for (int side = 0; side < RING_SEGMENTS; side++)
                {
                    float cos1 = RING_COS[side];
                    float sin1 = RING_SIN[side];
                    float cos2 = RING_COS[side + 1];
                    float sin2 = RING_SIN[side + 1];

                    this.ringPoint(this.ringA, pointA, normalA, binormalA, cos1, sin1, radiusA);
                    this.ringPoint(this.ringB, pointB, normalB, binormalB, cos1, sin1, radiusB);
                    this.ringPoint(this.ringC, pointB, normalB, binormalB, cos2, sin2, radiusB);
                    this.ringPoint(this.ringD, pointA, normalA, binormalA, cos2, sin2, radiusA);

                    float shade1 = RING_SHADE[side];
                    float shade2 = RING_SHADE[side + 1];
                    float r1 = this.getRed(color, shade1);
                    float g1 = this.getGreen(color, shade1);
                    float b1 = this.getBlue(color, shade1);
                    float r2 = this.getRed(color, shade2);
                    float g2 = this.getGreen(color, shade2);
                    float b2 = this.getBlue(color, shade2);

                    builder.vertex(model, this.ringA.x, this.ringA.y, this.ringA.z).color(r1, g1, b1, color.a).next();
                    builder.vertex(model, this.ringB.x, this.ringB.y, this.ringB.z).color(r1, g1, b1, color.a).next();
                    builder.vertex(model, this.ringC.x, this.ringC.y, this.ringC.z).color(r2, g2, b2, color.a).next();
                    builder.vertex(model, this.ringD.x, this.ringD.y, this.ringD.z).color(r2, g2, b2, color.a).next();
                }
            }
        }
    }

    /**
     * The offset copy of the line for one strand. A single strand (or no spread) is
     * the centre line itself - no copy at all. Everything else writes into a reused
     * buffer: this runs for every ring of every frame, so it must not allocate.
     */
    private Vector3f[] buildStrand(Vector3f[] center, int strand, int strandCount, float spread)
    {
        if (strandCount == 1 || spread <= MIN_DISTANCE)
        {
            return center;
        }

        this.strandBuffer = ensureVectors(this.strandBuffer, center.length);
        this.computeFrames(center);

        for (int i = 0; i < center.length; i++)
        {
            Vector3f normal = this.frameNormals[i];
            Vector3f binormal = this.frameBinormals[i];
            float progress = i / (float) (center.length - 1);
            float phase = TWO_PI * strand / strandCount + progress * TWO_PI * 1.35F;
            float offset1 = (float) Math.cos(phase) * spread;
            float offset2 = (float) Math.sin(phase) * spread;
            Vector3f point = this.strandBuffer[i];

            point.set(center[i]);
            point.x += normal.x * offset1 + binormal.x * offset2;
            point.y += normal.y * offset1 + binormal.y * offset2;
            point.z += normal.z * offset1 + binormal.z * offset2;
        }

        return this.strandBuffer;
    }

    /**
     * Build the rotation-minimising-ish frame of every point in one pass, into the
     * reused frame buffers. The old code rebuilt a frame per ring end, so every
     * interior point was solved twice per strand and allocated six vectors each time.
     */
    private void computeFrames(Vector3f[] points)
    {
        this.frameNormals = ensureVectors(this.frameNormals, points.length);
        this.frameBinormals = ensureVectors(this.frameBinormals, points.length);

        for (int index = 0; index < points.length; index++)
        {
            Vector3f tangent = this.scratchTangent;

            if (index == 0)
            {
                tangent.set(points[1]).sub(points[0]);
            }
            else if (index == points.length - 1)
            {
                tangent.set(points[index]).sub(points[index - 1]);
            }
            else
            {
                tangent.set(points[index + 1]).sub(points[index - 1]);
            }

            if (tangent.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
            {
                tangent.set(0F, 1F, 0F);
            }
            else
            {
                tangent.normalize();
            }

            Vector3f normal = this.frameNormals[index];
            Vector3f binormal = this.frameBinormals[index];

            this.scratchReference.set(Math.abs(tangent.y) < 0.9F ? 0F : 1F, Math.abs(tangent.y) < 0.9F ? 1F : 0F, 0F);
            this.scratchReference.cross(tangent, normal);

            if (normal.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
            {
                normal.set(1F, 0F, 0F);
            }
            else
            {
                normal.normalize();
            }

            binormal.set(tangent).cross(normal).normalize();
        }
    }

    private void ringPoint(Vector3f out, Vector3f point, Vector3f normal, Vector3f binormal, float cos, float sin, float radius)
    {
        float a = cos * radius;
        float b = sin * radius;

        out.set(
            point.x + normal.x * a + binormal.x * b,
            point.y + normal.y * a + binormal.y * b,
            point.z + normal.z * a + binormal.z * b
        );
    }

    /**
     * Resize a scratch array of vectors, keeping the instances that already exist.
     * The result is exactly {@code size} long - callers iterate it by length, so a
     * buffer left over from a longer rope would draw phantom segments.
     */
    private static Vector3f[] ensureVectors(Vector3f[] array, int size)
    {
        if (array.length == size)
        {
            return array;
        }

        Vector3f[] resized = new Vector3f[size];
        int kept = Math.min(array.length, size);

        System.arraycopy(array, 0, resized, 0, kept);

        for (int i = kept; i < size; i++)
        {
            resized[i] = new Vector3f();
        }

        return resized;
    }

    private float getTaper(float progress)
    {
        return Math.max(0.08F, 1F - MathUtils.clamp(this.form.taper.get(), 0F, 1F) * progress);
    }

    private float getRed(Color color, float shade)
    {
        return MathUtils.clamp(color.r * shade, 0F, 1F);
    }

    private float getGreen(Color color, float shade)
    {
        return MathUtils.clamp(color.g * shade, 0F, 1F);
    }

    private float getBlue(Color color, float shade)
    {
        return MathUtils.clamp(color.b * shade, 0F, 1F);
    }

    private Color getPickingColor(int index)
    {
        return new Color(
            (index & 0xff) / 255F,
            (index >> 8 & 0xff) / 255F,
            (index >> 16 & 0xff) / 255F,
            1F
        );
    }

    private Matrix4f getWorldMatrix(FormRenderingContext context)
    {
        return context.world == null ? new Matrix4f() : new Matrix4f(context.world.peek().getPositionMatrix());
    }

    private Matrix4f getInverse(Matrix4f matrix)
    {
        Matrix4f inverse = new Matrix4f(matrix);

        if (Math.abs(inverse.determinant()) > 1.0E-8F)
        {
            inverse.invert();
        }
        else
        {
            inverse.identity();
        }

        return inverse;
    }

    private Vector3f transformPosition(Matrix4f matrix, Vector3f position)
    {
        return matrix.transformPosition(new Vector3f(position));
    }

    private PhysicsState getState(IEntity entity)
    {
        if (entity == null)
        {
            return this.nullEntityState;
        }

        return this.states.computeIfAbsent(entity, (key) -> new PhysicsState());
    }

    /**
     * Simulation clock in ticks, with the frame's partial tick folded in. The
     * fraction is what lets the solve advance (and the render interpolate)
     * between game ticks instead of once every 50 ms.
     */
    private double getSimulationTime(FormRenderingContext context)
    {
        float transition = MathUtils.clamp(context.getTransition(), 0F, 1F);

        if (context.modelRenderer)
        {
            return context.modelRendererTick + transition;
        }

        return context.entity == null ? transition : context.entity.getAge() + transition;
    }

    private void updateSimulation(PhysicsState state, FormRenderingContext context, double time, Vector3f start, Vector3f endpoint)
    {
        if (state.lastTime == Double.NEGATIVE_INFINITY)
        {
            state.lastTime = time;
        }

        double delta = time - state.lastTime;

        state.lastTime = time;

        if (delta < 0D || delta > MAX_TICK_STEP)
        {
            Vector3f resetEndpoint = state.anchorMode == WebForm.ANCHOR_LOCKED ? new Vector3f(state.lockedEnd) : endpoint;

            state.reset(state.segments, state.anchorMode, true, start, resetEndpoint, this.form.length.get(), this.form.sag.get());

            return;
        }

        if (this.form.paused.get())
        {
            state.accumulator = 0F;
            state.applyPinsTo(state.points, start, endpoint);
            state.snapshot();

            return;
        }

        /* The speed multiplier stretches the simulation clock: at 1 the rope runs on
         * game time, at 3 it swings three times as fast without touching gravity or
         * the constraint solve, so the motion stays the same shape. */
        float speed = MathUtils.clamp(this.form.speed.get(), 0F, WebForm.MAX_SPEED);

        state.accumulator += (float) (delta * speed);

        int steps = 0;

        while (state.accumulator >= 1F && steps < MAX_STEPS_PER_FRAME)
        {
            state.snapshot();
            this.integrate(state, state.step++);
            this.applyPins(state, start, endpoint);
            this.solveConstraints(state, context, this.form.iterations.get(), start, endpoint);

            state.accumulator -= 1F;
            steps += 1;
        }

        if (state.accumulator >= 1F)
        {
            /* A frame that fell behind (or a huge speed) must not build a backlog
             * that then plays back in slow motion - the leftover time is dropped. */
            state.accumulator = 0F;
        }

        this.applyPins(state, start, endpoint);

        /* World collisions can only push points; a solver that lost a point to
         * infinity (or to the other side of the map) is restarted from the rest
         * shape instead of dragging a stretched web around forever. */
        if (!state.isSane(start, Math.max(MAX_POINT_DISTANCE, state.restLength * 2F + 16F)))
        {
            Vector3f resetEndpoint = state.anchorMode == WebForm.ANCHOR_LOCKED ? new Vector3f(state.lockedEnd) : endpoint;

            state.reset(state.segments, state.anchorMode, true, start, resetEndpoint, this.form.length.get(), this.form.sag.get());
        }
    }


    /**
     * Gather the weights the body parts put on the rope. A part loads the point its
     * own attachment name resolves to, so where it hangs matters: a giant on the tip
     * drags the whole line, the same giant near the shooter barely bends it. Parts
     * with the weight toggle off are massless, which is the default and keeps the
     * old behaviour exactly.
     */
    private void collectMasses(int segments)
    {
        if (this.pointMass.length != segments)
        {
            this.pointMass = new float[segments];
        }

        java.util.Arrays.fill(this.pointMass, 0F);

        this.loaded = false;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            if (!part.weightEnabled.get() || part.getForm() == null)
            {
                continue;
            }

            float weight = Math.max(0F, part.weight.get());

            if (weight <= 0F)
            {
                continue;
            }

            int index = this.attachmentIndex(part.bone.get(), segments);

            if (index < 0)
            {
                continue;
            }

            this.pointMass[index] += weight;
            this.loaded = true;
        }
    }

    /** The rope point an attachment name resolves to, or -1 when it isn't one of ours. */
    private int attachmentIndex(String bone, int segments)
    {
        if (bone == null || bone.isEmpty() || segments <= 0)
        {
            return -1;
        }

        String name = bone.endsWith(FIXED_SUFFIX) ? bone.substring(0, bone.length() - FIXED_SUFFIX.length()) : bone;
        int index;

        if (name.equals(BONE_START))
        {
            index = 0;
        }
        else if (name.equals(BONE_END))
        {
            index = segments - 1;
        }
        else if (name.equals(BONE_MIDDLE))
        {
            index = segments / 2;
        }
        else if (name.startsWith(BONE_POINT_PREFIX))
        {
            try
            {
                index = Integer.parseInt(name.substring(BONE_POINT_PREFIX.length())) - 1;
            }
            catch (NumberFormatException e)
            {
                return -1;
            }
        }
        else
        {
            return -1;
        }

        return MathUtils.clamp(index, 0, segments - 1);
    }

    /**
     * Inverse mass of a point: 1 for a bare rope point, smaller the more weight hangs
     * on it. The constraint solve shares every correction by these, so the line gives
     * way toward a heavy load (it visibly stretches) and snaps a light one around.
     */
    private float getInverseMass(int index)
    {
        if (!this.loaded || index < 0 || index >= this.pointMass.length)
        {
            return 1F;
        }

        return POINT_MASS / (POINT_MASS + this.pointMass[index]);
    }

    private void integrate(PhysicsState state, long step)
    {
        int last = state.points.length - 1;
        int end = state.anchorMode == WebForm.ANCHOR_FREE ? last + 1 : last;
        float damping = 1F - MathUtils.clamp(this.form.damping.get(), 0F, 0.99F);
        float gravity = Math.max(0F, this.form.gravity.get()) * GRAVITY_SCALE;
        Vector3f wind = this.form.wind.get();
        float windSpeed = Math.max(0F, this.form.windSpeed.get());
        float noiseAmount = MathUtils.clamp(this.form.windNoise.get(), 0F, 1F);

        for (int i = 1; i < end; i++)
        {
            Vector3f point = state.points[i];
            Vector3f previous = state.previous[i];

            /* Air drag barely slows a heavy body: the more mass hangs here, the less
             * of the damping applies, so a loaded line carries its swing further and
             * whips faster instead of settling like an empty thread. */
            float load = this.loaded ? 1F - this.getInverseMass(i) : 0F;
            float pointDamping = 1F - (1F - damping) * (1F + (HEAVY_DAMPING_SCALE - 1F) * load);

            float velocityX = (point.x - previous.x) * pointDamping;
            float velocityY = (point.y - previous.y) * pointDamping;
            float velocityZ = (point.z - previous.z) * pointDamping;
            float phase = step * 0.08F * windSpeed + i * 1.713F;
            float noiseX = (float) Math.sin(phase * 1.31F) * noiseAmount;
            float noiseY = (float) Math.cos(phase * 0.87F + 0.8F) * noiseAmount;
            float noiseZ = (float) Math.sin(phase * 1.11F + 1.7F) * noiseAmount;

            previous.set(point);
            point.set(
                point.x + velocityX + (wind.x + noiseX) * WIND_SCALE,
                point.y + velocityY - gravity + (wind.y + noiseY) * WIND_SCALE,
                point.z + velocityZ + (wind.z + noiseZ) * WIND_SCALE
            );

            /* A depenetration push shows up as velocity on the next step. Left
             * unbounded, one deep contact launches the point across the world and
             * the rest of the rope follows it - clamp the per-tick travel. */
            this.clampStep(point, previous);
        }
    }

    private void solveConstraints(PhysicsState state, FormRenderingContext context, int requestedIterations, Vector3f start, Vector3f endpoint)
    {
        int iterations = MathUtils.clamp(requestedIterations, 1, 12);
        float segmentLength = Math.max(MIN_DISTANCE, state.restLength / (state.points.length - 1));
        float stiffness = MathUtils.clamp(this.form.stiffness.get(), 0F, 1F);
        int last = state.points.length - 1;
        boolean pinEnd = state.anchorMode != WebForm.ANCHOR_FREE;

        for (int iteration = 0; iteration < iterations; iteration++)
        {
            for (int i = 0; i < last; i++)
            {
                Vector3f first = state.points[i];
                Vector3f second = state.points[i + 1];
                float dx = second.x - first.x;
                float dy = second.y - first.y;
                float dz = second.z - first.z;
                float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance <= MIN_DISTANCE)
                {
                    continue;
                }

                float error = distance - segmentLength;
                float correction = error / distance * stiffness;
                boolean firstPinned = i == 0;
                boolean secondPinned = i + 1 == last && pinEnd;

                if (!firstPinned && !secondPinned)
                {
                    /* Share the correction by inverse mass: the lighter end travels,
                     * the heavier one holds its ground. With no weights attached both
                     * are 1 and this is the old even 50/50 split. */
                    float inverseFirst = this.getInverseMass(i);
                    float inverseSecond = this.getInverseMass(i + 1);
                    float total = inverseFirst + inverseSecond;
                    float shareFirst = total <= MIN_DISTANCE ? 0.5F : inverseFirst / total;
                    float shareSecond = total <= MIN_DISTANCE ? 0.5F : inverseSecond / total;

                    first.x += dx * correction * shareFirst;
                    first.y += dy * correction * shareFirst;
                    first.z += dz * correction * shareFirst;
                    second.x -= dx * correction * shareSecond;
                    second.y -= dy * correction * shareSecond;
                    second.z -= dz * correction * shareSecond;
                }
                else if (firstPinned)
                {
                    second.x -= dx * correction;
                    second.y -= dy * correction;
                    second.z -= dz * correction;
                }
                else
                {
                    first.x += dx * correction;
                    first.y += dy * correction;
                    first.z += dz * correction;
                }
            }

            if (iteration == iterations - 1 && this.form.collisions.get() && context.entity != null && context.entity.getWorld() != null)
            {
                float radius = Math.max(0.05F, this.form.thickness.get() * 0.6F);
                int end = pinEnd ? last : last + 1;

                ModelPhysicsWorldCollisions.resolve(context.entity.getWorld(), state.points, state.previous, 1, end, radius, 0.35F);
            }

            /* Re-pin to the real anchors. Passing the (possibly depenetrated) points
             * themselves used to pin the rope to wherever a collision had shoved its
             * ends, so every contact walked the whole web away from the shooter. */
            this.applyPins(state, start, endpoint);
        }
    }

    private void applyPins(PhysicsState state, Vector3f start, Vector3f endpoint)
    {
        int last = state.points.length - 1;

        state.points[0].set(start);
        state.previous[0].set(start);

        if (state.anchorMode != WebForm.ANCHOR_FREE)
        {
            state.points[last].set(endpoint);
            state.previous[last].set(endpoint);
        }
    }

    /** Cap how far a point may have moved since the last tick, keeping its direction. */
    private void clampStep(Vector3f point, Vector3f previous)
    {
        float dx = point.x - previous.x;
        float dy = point.y - previous.y;
        float dz = point.z - previous.z;
        float lengthSq = dx * dx + dy * dy + dz * dz;

        if (!Float.isFinite(lengthSq))
        {
            point.set(previous);

            return;
        }

        if (lengthSq <= MAX_STEP * MAX_STEP)
        {
            return;
        }

        float scale = MAX_STEP / (float) Math.sqrt(lengthSq);

        point.set(previous.x + dx * scale, previous.y + dy * scale, previous.z + dz * scale);
    }

    private Vector3f[] buildStaticPoints(Vector3f start, Vector3f end, int segments, float sag)
    {
        Vector3f[] points = new Vector3f[segments];

        for (int i = 0; i < segments; i++)
        {
            float progress = i / (float) (segments - 1);
            Vector3f point = new Vector3f(start).lerp(end, progress);

            point.y -= Math.max(0F, sag) * (float) Math.sin(Math.PI * progress);
            points[i] = point;
        }

        return points;
    }

    public void resetSimulation()
    {
        this.states.clear();
        this.nullEntityState.clear();
    }

    private static class PhysicsState
    {
        public Vector3f[] points = new Vector3f[0];
        public Vector3f[] previous = new Vector3f[0];

        /** Positions before the last solved step, and the buffer the frame renders. */
        public Vector3f[] past = new Vector3f[0];
        public Vector3f[] rendered = new Vector3f[0];

        public Vector3f lockedEnd = new Vector3f();
        public Vector3f initialEnd = new Vector3f();

        /** Simulation clock (in ticks, fractional) of the last frame, and the unspent remainder. */
        public double lastTime = Double.NEGATIVE_INFINITY;
        public float accumulator;
        public long step;

        public int segments;
        public int anchorMode;
        public boolean simulation;
        public float lengthSetting;
        public float sagSetting;
        public float restLength;

        public boolean needsReset(int segments, int anchorMode, boolean simulation, float length, float sag)
        {
            return this.points.length != segments || this.segments != segments || this.anchorMode != anchorMode
                || this.simulation != simulation || Math.abs(this.lengthSetting - length) > 1.0E-4F
                || Math.abs(this.sagSetting - sag) > 1.0E-4F;
        }

        public void reset(int segments, int anchorMode, boolean simulation, Vector3f start, Vector3f end, float length, float sag)
        {
            this.segments = segments;
            this.anchorMode = anchorMode;
            this.simulation = simulation;
            this.lengthSetting = length;
            this.sagSetting = sag;
            this.lockedEnd.set(end);
            this.initialEnd.set(end);
            this.restLength = Math.max(MIN_DISTANCE, Math.max(length, start.distance(end)));
            this.points = new Vector3f[segments];
            this.previous = new Vector3f[segments];
            this.past = new Vector3f[segments];
            this.rendered = new Vector3f[segments];

            for (int i = 0; i < segments; i++)
            {
                float progress = i / (float) (segments - 1);
                Vector3f point = new Vector3f(start).lerp(end, progress);

                point.y -= Math.max(0F, sag) * (float) Math.sin(Math.PI * progress);
                this.points[i] = point;
                this.previous[i] = new Vector3f(point);
                this.past[i] = new Vector3f(point);
                this.rendered[i] = new Vector3f(point);
            }

            this.accumulator = 0F;
        }

        public void clear()
        {
            this.points = new Vector3f[0];
            this.previous = new Vector3f[0];
            this.past = new Vector3f[0];
            this.rendered = new Vector3f[0];
            this.lastTime = Double.NEGATIVE_INFINITY;
            this.accumulator = 0F;
        }

        /** Remember where the rope stood before the step that is about to run. */
        public void snapshot()
        {
            for (int i = 0; i < this.points.length; i++)
            {
                this.past[i].set(this.points[i]);
            }
        }

        /**
         * The rope as this frame should see it: the last two solved states blended by
         * the unspent part of the tick. The pinned ends are snapped to their live
         * anchors afterwards, so the web never lags behind the hand holding it.
         */
        public Vector3f[] interpolate(Vector3f start, Vector3f endpoint)
        {
            float alpha = MathUtils.clamp(this.accumulator, 0F, 1F);

            for (int i = 0; i < this.points.length; i++)
            {
                this.rendered[i].set(this.past[i]).lerp(this.points[i], alpha);
            }

            this.applyPinsTo(this.rendered, start, endpoint);

            return this.rendered;
        }

        /** Snap the anchored ends of an array of points onto their live anchors. */
        public void applyPinsTo(Vector3f[] target, Vector3f start, Vector3f endpoint)
        {
            if (target.length == 0)
            {
                return;
            }

            target[0].set(start);

            if (this.anchorMode != WebForm.ANCHOR_FREE)
            {
                target[target.length - 1].set(endpoint);
            }
        }

        /**
         * Whether the solve is still usable: every point finite and within reach of
         * the shooter. A single NaN (or a point flung across the world by a bad
         * contact) poisons the whole rope, so the state is rebuilt instead.
         */
        public boolean isSane(Vector3f start, float maxDistance)
        {
            float maxSq = maxDistance * maxDistance;

            for (int i = 0; i < this.points.length; i++)
            {
                Vector3f point = this.points[i];
                Vector3f previous = this.previous[i];

                if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z)
                    || !Float.isFinite(previous.x) || !Float.isFinite(previous.y) || !Float.isFinite(previous.z))
                {
                    return false;
                }

                if (point.distanceSquared(start) > maxSq)
                {
                    return false;
                }
            }

            return true;
        }
    }
}
