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

    /* How the simulation clock moved since the last frame */

    /** Ordinary playback: step the solver by the elapsed time. */
    private static final int TIME_NORMAL = 0;

    /**
     * Standing still. Paused playback keeps handing out partial ticks that wobble
     * back and forth inside the same tick, which used to read as the clock running
     * backwards and threw the whole rope back to its rest shape every frame.
     */
    private static final int TIME_HOLD = 1;

    /** The timeline was scrubbed, or the scene jumped: the rope has to be re-settled. */
    private static final int TIME_JUMP = 2;

    /** Backwards wobble this small is paused playback, not a scrub. */
    private static final double HOLD_TOLERANCE = 1.5D;

    /** Solver steps spent settling the rope after a scrub, instead of showing it fall. */
    private static final int SETTLE_STEPS = 120;
    private static final float MIN_DISTANCE = 1.0E-4F;

    /**
     * Blocks per tick squared at a gravity of 1. Vanilla entities fall at about
     * 0.08, and the old 0.02 made every web a slow motion pendulum: a five block
     * line took seven seconds to swing once, so a hanging body just oozed downwards
     * instead of swinging. At this scale the default 0.45 gives a rope that swings
     * like a rope on film.
     */
    private static final float GRAVITY_SCALE = 0.06F;

    /**
     * How much of its speed a point may lose per tick at a damping of 1. The slider
     * used to be the loss itself, so the default 0.08 wiped 8% of the speed every
     * tick - a swing was over in half a second and never got to be a swing. Now the
     * default costs about 0.6% a tick: five or six visible passes that decay to a
     * stop right under the anchor, which is what a body on a rope does.
     */
    private static final float DAMPING_SCALE = 0.08F;

    /**
     * Fraction of the speed difference between neighbouring points removed each
     * tick. This is what kills the shivering - it only ever damps points moving
     * against each other, never the rope swinging as a whole - and it is why the
     * air damping above can afford to be so gentle.
     */
    private static final float SEGMENT_DAMPING = 0.25F;
    private static final float WIND_SCALE = 0.02F;

    /** Furthest a simulated point may sit from the shooter before the rope is restarted. */
    private static final float MAX_POINT_DISTANCE = 256F;

    /** Furthest a simulated point may travel in one tick. */
    private static final float MAX_STEP = 2F;

    /** How quickly an attachment's orientation follows the rope (0 frozen, 1 instant). */
    private static final float ORIENT_SMOOTHING = 0.18F;

    /**
     * Mass of one bare rope point, in the same kilograms the body parts use. A load
     * is only felt relative to the rope itself, so this is what "heavy" is measured
     * against: a 60 kg rider on a 2 kg point moves the line, a 0.5 kg rat barely does.
     */
    private static final float POINT_MASS = 2F;

    /** Damping left at a fully loaded point - heavy things keep their momentum. */
    private static final float HEAVY_DAMPING_SCALE = 0.55F;

    /** Lightest a loaded point may be treated as, for solver stability. */
    private static final float MIN_INVERSE_MASS = 0.15F;

    /** Fraction of a segment a single constraint pass may move a point. */
    private static final float MAX_CORRECTION = 0.5F;

    /* Web shooter phases of one shot */

    /** Loaded, nothing drawn: both ends sit in the hand. */
    private static final int SHOT_IDLE = 0;

    /** The tip is travelling from the hand towards the target. */
    private static final int SHOT_FLYING = 1;

    /** The shot arrived and turned into a rope (stuck, or released and falling). */
    private static final int SHOT_ROPE = 2;

    /** A single shot that arrived: only the splat (and the last of the glob) is left. */
    private static final int SHOT_LANDED = 3;

    /** Spokes of the splattered web patch, and how many rings tie them together. */
    private static final int SPLAT_SPOKES = 9;
    private static final int SPLAT_RINGS = 2;

    /** Ticks the splat takes to pop to full size when it hits. */
    private static final float SPLAT_GROW = 2.5F;

    /** Seconds of the dissolve timer spent fading out. */
    private static final float DISSOLVE_FADE = 1F;

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

    /* Shooter scratch: the flying line, and the polyline the splat is built from. */
    private Vector3f[] shotBuffer = new Vector3f[0];
    private Vector3f[] frameBuffer = new Vector3f[0];
    private Vector3f[] splatBuffer = new Vector3f[0];
    private final Vector3f scratchSplatU = new Vector3f();
    private final Vector3f scratchSplatV = new Vector3f();
    private final Vector3f scratchSplatPoint = new Vector3f();

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

        /* The direction is taken over several segments, not between two neighbours.
         * One segment is a few centimetres long and every solver pass nudges it, so
         * a hanging body read off it wobbles constantly; a chord over a quarter of
         * the rope points the same way but barely notices the ripple. */
        int span = Math.max(1, Math.min(points.length / 4, 4));
        int reference = index == 0 ? Math.min(points.length - 1, span) : Math.max(0, index - span);
        Vector3f up = new Vector3f();

        if (index == 0)
        {
            up.set(points[0]).sub(points[reference]);
        }
        else
        {
            up.set(points[reference]).sub(points[index]);
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
        Vector3f splatCenter = null;
        Vector3f splatNormal = null;
        float fade = 1F;

        /* One clock read per frame, before anything that depends on time. */
        this.advanceClock(state, this.getSimulationTime(context));

        /* The web shooter runs its own little state machine in front of the solver:
         * holstered draws nothing, a flying shot is a straight taut line the physics
         * never touches, and only once it lands does the rope take over. */
        if (this.form.shooter.get())
        {
            double shotTime = state.clock;

            this.updateShot(state, shotTime, startWorld, endpointWorld);
            fade = this.getDissolveFade(state, shotTime);

            if (state.shotPhase == SHOT_IDLE || fade <= 0F)
            {
                /* Nothing is drawn, but body parts still need somewhere to sit: the
                 * hand while holstered, the last known target once it dissolved. */
                this.collapseAttachPoints(inverseWorld,
                    state.shotPhase == SHOT_IDLE ? startWorld : state.shotTarget, segments);

                if (state.shotPhase == SHOT_IDLE)
                {
                    this.renderShooterMarker(context);
                }

                return;
            }

            if (state.shotPhase == SHOT_FLYING || state.shotPhase == SHOT_LANDED)
            {
                Vector3f[] flight = this.buildShotPoints(state, startWorld, segments);
                Vector3f[] flightLocal = null;

                if (flight != null)
                {
                    flightLocal = this.toLocal(inverseWorld, flight);
                    this.attachPoints = flightLocal;
                }
                else
                {
                    this.collapseAttachPoints(inverseWorld, state.shotTarget, segments);
                }

                if (this.hasSplat(state))
                {
                    splatCenter = this.transformPosition(inverseWorld, state.shotTarget);
                    splatNormal = new Vector3f(state.shotTarget).sub(state.shotOrigin);
                }

                this.renderPoints(context, flightLocal, splatCenter, splatNormal, fade, this.getSplatScale(state, shotTime));

                return;
            }

            /* Landed and turned into a rope: the tip is world locked when it stuck,
             * or completely free when it was a shot into thin air. */
            anchorMode = state.shotMode == WebForm.SHOT_AIR ? WebForm.ANCHOR_FREE : WebForm.ANCHOR_LOCKED;
            endpointWorld = new Vector3f(state.shotTarget);

            if (this.hasSplat(state))
            {
                splatCenter = this.transformPosition(inverseWorld, state.shotTarget);
                splatNormal = new Vector3f(state.shotTarget).sub(state.shotOrigin);
            }
        }
        else if (state.shotPhase != SHOT_IDLE)
        {
            /* Shooter mode switched off mid-shot: forget the shot entirely. */
            state.shotPhase = SHOT_IDLE;
            state.shotResetPending = false;
        }

        if (state.shotResetPending)
        {
            /* The rope is born exactly where the shot ended, taut between the hand
             * and the point it reached - no sag, or it would snap into a curve the
             * frame it lands. */
            state.reset(segments, anchorMode, simulate, startWorld, endpointWorld, this.form.length.get(), 0F);
            state.applyShotLanding(startWorld, endpointWorld, this.form.shotSpeed.get(), state.shotMode == WebForm.SHOT_AIR);
            state.shotResetPending = false;
        }
        else if (state.needsReset(simulate))
        {
            state.reset(segments, anchorMode, simulate, startWorld, endpointWorld, this.form.length.get(), this.form.sag.get());
        }
        else
        {
            /* Everything short of a rebuild is done in place, so animating these
             * from a film does not throw the rope away sixty times a second: the
             * point count is resampled along the rope it already has, and the
             * anchor mode just changes which ends are held. */
            if (state.points.length != segments)
            {
                state.resample(segments);
            }

            if (state.anchorMode != anchorMode)
            {
                state.setAnchorMode(anchorMode, endpointWorld);
            }
        }

        /* Length and sag are read every frame instead of forcing a reset, which is
         * what made them useless as keyframe channels. */
        state.syncRest(this.form.length.get(), this.form.sag.get(), startWorld, endpointWorld);

        Vector3f endWorld = endpointWorld;

        if (anchorMode == WebForm.ANCHOR_LOCKED)
        {
            endWorld = new Vector3f(state.lockedEnd);
        }

        Vector3f[] worldPoints;

        this.collectMasses(segments);

        if (simulate)
        {
            this.updateSimulation(state, context, state.clock, startWorld, endWorld);

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

        this.renderPoints(context, localPoints, splatCenter, splatNormal, fade,
            this.getSplatScale(state, state.clock));
    }

    /**
     * Advance the shot. The trigger is a plain value, so this only ever reacts to it:
     * off holsters the web, the frame it turns on captures where the hand and the
     * target are and starts the clock. Everything after that is a function of the
     * elapsed time, which means scrubbing a film backwards rewinds the shot instead
     * of leaving it stuck at its end state.
     */
    private void updateShot(PhysicsState state, double time, Vector3f startWorld, Vector3f endpointWorld)
    {
        int mode = MathUtils.clamp(this.form.shotMode.get(), WebForm.SHOT_ANCHORED, WebForm.SHOT_SINGLE);

        if (!this.form.fire.get())
        {
            if (state.shotPhase != SHOT_IDLE)
            {
                state.shotPhase = SHOT_IDLE;
                state.shotResetPending = false;
                state.landTime = Double.NEGATIVE_INFINITY;
            }

            return;
        }

        if (this.form.paused.get())
        {
            /* Paused freezes the shot too. The trigger time rides along with the
             * clock, so nothing has advanced when playback picks up again. */
            state.fireTime += state.timeDelta;

            return;
        }

        /* Fresh trigger, a different kind of shot, or the clock jumped back: fire
         * anew. Moving the end point does NOT re-shoot on its own - a web that stuck
         * to a wall must not chase a keyframed aim - the editor's own inputs ask for
         * a reset instead. */
        if (state.shotPhase == SHOT_IDLE || state.shotMode != mode || time < state.fireTime)
        {
            state.shotMode = mode;
            state.shotPhase = SHOT_FLYING;
            state.fireTime = time;
            state.landTime = Double.NEGATIVE_INFINITY;
            state.shotResetPending = false;
            state.shotOrigin.set(startWorld);
            state.shotTarget.set(endpointWorld);
        }

        /* The target is captured once, in world space, and never chases the shooter
         * afterwards: a web goes where it was aimed, and that is the whole point of
         * anchoring it - the hand may swing off, the web stays on the building. */

        float speed = Math.max(0.01F, this.form.shotSpeed.get());
        float distance = state.shotOrigin.distance(state.shotTarget);

        /* Dragging the playhead cannot know when the trigger went off, so a shot
         * caught by a scrub is presented already landed rather than replaying its
         * flight from the moment the scene was scrubbed to. Playing the film
         * normally still animates every shot in full. */
        if (state.timeStatus == TIME_JUMP)
        {
            state.fireTime = time - distance / speed - 1D;
        }

        state.shotTravelled = (float) Math.max(0D, (time - state.fireTime) * speed);

        if (state.shotTravelled >= distance && state.shotPhase == SHOT_FLYING)
        {
            state.shotPhase = state.shotMode == WebForm.SHOT_SINGLE ? SHOT_LANDED : SHOT_ROPE;
            state.landTime = time;
            state.shotResetPending = state.shotPhase == SHOT_ROPE;
        }
        else if (state.shotTravelled < distance && state.shotPhase != SHOT_FLYING)
        {
            /* Scrubbed back into the flight, or the target moved further away. */
            state.shotPhase = SHOT_FLYING;
            state.landTime = Double.NEGATIVE_INFINITY;
            state.shotResetPending = false;
        }
    }

    /**
     * The flying line in world space, or null once there is nothing left in the air.
     * An anchored or air shot trails all the way back to the hand; a single shot is a
     * short glob that leaves the hand behind and streaks towards the target.
     */
    private Vector3f[] buildShotPoints(PhysicsState state, Vector3f startWorld, int segments)
    {
        float distance = state.shotOrigin.distance(state.shotTarget);
        float tipProgress = distance <= MIN_DISTANCE ? 1F : MathUtils.clamp(state.shotTravelled / distance, 0F, 1F);
        Vector3f tip = new Vector3f(state.shotOrigin).lerp(state.shotTarget, tipProgress);
        Vector3f tail;

        if (state.shotMode == WebForm.SHOT_SINGLE)
        {
            float tailLength = Math.max(0.05F, this.form.shotTail.get());
            float tailProgress = distance <= MIN_DISTANCE
                ? 1F : MathUtils.clamp((state.shotTravelled - tailLength) / distance, 0F, 1F);

            if (tailProgress >= 1F)
            {
                return null;
            }

            tail = new Vector3f(state.shotOrigin).lerp(state.shotTarget, tailProgress);
        }
        else
        {
            tail = new Vector3f(startWorld);
        }

        int count = Math.max(2, segments);

        this.shotBuffer = ensureVectors(this.shotBuffer, count);

        for (int i = 0; i < count; i++)
        {
            float progress = i / (float) (count - 1);

            this.shotBuffer[i].set(tail).lerp(tip, progress);
        }

        return this.shotBuffer;
    }

    /** Whether this shot leaves a patch of web where it hit. Air shots stick to nothing. */
    private boolean hasSplat(PhysicsState state)
    {
        return this.form.splat.get() && state.shotMode != WebForm.SHOT_AIR
            && (state.shotPhase == SHOT_LANDED || state.shotPhase == SHOT_ROPE);
    }

    /** The splat pops to full size over a couple of ticks instead of blinking in. */
    private float getSplatScale(PhysicsState state, double time)
    {
        if (state.landTime == Double.NEGATIVE_INFINITY)
        {
            return 0F;
        }

        float elapsed = (float) Math.max(0D, time - state.landTime);
        float progress = MathUtils.clamp(elapsed / SPLAT_GROW, 0F, 1F);

        /* Ease out, with a touch of overshoot - webbing hits wet and settles. */
        return (float) (1D + 0.12D * Math.sin(Math.PI * progress)) * (progress * (2F - progress));
    }

    /**
     * Alpha of a landed web that is dissolving. Comic webbing gives out after a
     * while; 0 seconds keeps it forever, which is the default.
     */
    private float getDissolveFade(PhysicsState state, double time)
    {
        float dissolve = Math.max(0F, this.form.dissolve.get());

        if (dissolve <= 0F || state.landTime == Double.NEGATIVE_INFINITY)
        {
            return 1F;
        }

        float seconds = (float) Math.max(0D, (time - state.landTime) / 20D);
        float fadeStart = Math.max(0F, dissolve - DISSOLVE_FADE);

        if (seconds <= fadeStart)
        {
            return 1F;
        }

        if (seconds >= dissolve)
        {
            return 0F;
        }

        return 1F - (seconds - fadeStart) / Math.max(MIN_DISTANCE, dissolve - fadeStart);
    }

    /** Park every attachment point on one spot, so body parts ride the hidden shooter. */
    private void collapseAttachPoints(Matrix4f inverseWorld, Vector3f worldPoint, int segments)
    {
        int count = Math.max(2, segments);

        this.localBuffer = ensureVectors(this.localBuffer, count);

        Vector3f local = this.transformPosition(inverseWorld, worldPoint);

        for (int i = 0; i < count; i++)
        {
            this.localBuffer[i].set(local);
        }

        this.attachPoints = this.localBuffer;
    }

    /** World points into the render stack's local space, reusing the frame buffer. */
    private Vector3f[] toLocal(Matrix4f inverseWorld, Vector3f[] worldPoints)
    {
        this.localBuffer = ensureVectors(this.localBuffer, worldPoints.length);

        for (int i = 0; i < worldPoints.length; i++)
        {
            inverseWorld.transformPosition(this.localBuffer[i].set(worldPoints[i]));
        }

        return this.localBuffer;
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
        this.renderPoints(context, points, null, null, 1F, 0F);
    }

    /**
     * Draw the web: the line itself (when there is one in the air or on the rope) and
     * the splattered patch where a shot landed, both in one buffer so a translucent
     * web is still a single sorted draw.
     */
    private void renderPoints(FormRenderingContext context, Vector3f[] points, Vector3f splatCenter, Vector3f splatNormal, float fade, float splatScale)
    {
        boolean hasLine = points != null && points.length >= 2;
        boolean hasSplat = splatCenter != null && splatScale > 0F;

        if ((!hasLine && !hasSplat) || fade <= 0F)
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

        color.a *= MathUtils.clamp(fade, 0F, 1F);

        float alpha = color.a;
        int renderLayer = this.form.renderLayer.get();
        boolean forcedOpaque = renderLayer == Form.LAYER_SOLID || renderLayer == Form.LAYER_CUTOUT;
        boolean translucent = !forcedOpaque && (renderLayer == Form.LAYER_TRANSLUCENT || alpha < 0.999F);
        boolean defer = translucent && !context.isPicking() && !context.ui && context.type != FormRenderType.ITEM_INVENTORY
            && FormTranslucentQueue.isActive();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        if (hasLine)
        {
            this.appendTube(builder, model, points, color);
        }

        if (hasSplat)
        {
            this.appendSplat(builder, model, splatCenter, splatNormal, this.form.splatSize.get() * splatScale, color);
        }

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
     * The patch of webbing a shot leaves where it hits: a handful of radial spokes
     * with two rings strung between them, drawn facing back along the flight path.
     * That is what a web splat reads as at any distance, and it costs a few dozen
     * quads - far cheaper than a texture with its own material and sorting.
     */
    private void appendSplat(BufferBuilder builder, Matrix4f model, Vector3f center, Vector3f direction, float radius, Color color)
    {
        if (radius <= MIN_DISTANCE)
        {
            return;
        }

        Vector3f normal = this.scratchSplatU.set(direction);

        if (normal.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
        {
            normal.set(0F, 1F, 0F);
        }

        normal.normalize();

        /* An in-plane basis for the patch; the reference swaps near the poles so the
         * pattern never collapses onto a line. */
        Vector3f right = this.scratchSplatV.set(Math.abs(normal.y) < 0.9F ? 0F : 1F, Math.abs(normal.y) < 0.9F ? 1F : 0F, 0F);

        right.cross(normal).normalize();

        Vector3f up = new Vector3f(normal).cross(right).normalize();
        float thickness = Math.max(0.0005F, this.form.thickness.get() * 0.32F);

        /* Lift the patch a hair off the surface it is stuck to, and dish it slightly
         * towards the shooter so it never z-fights with a flat wall. */
        Vector3f base = new Vector3f(center).sub(normal.x * radius * 0.04F, normal.y * radius * 0.04F, normal.z * radius * 0.04F);
        float[] spokeRadius = new float[SPLAT_SPOKES];

        this.splatBuffer = ensureVectors(this.splatBuffer, Math.max(4, SPLAT_SPOKES + 1));

        for (int spoke = 0; spoke < SPLAT_SPOKES; spoke++)
        {
            /* Deterministic irregularity: a real splat is never a clean flower, but
             * it must also not jitter from frame to frame. */
            float wobble = 0.78F + 0.22F * (float) Math.sin(spoke * 2.399F);

            spokeRadius[spoke] = radius * wobble;
        }

        for (int spoke = 0; spoke < SPLAT_SPOKES; spoke++)
        {
            float angle = spoke / (float) SPLAT_SPOKES * TWO_PI;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            for (int i = 0; i < 4; i++)
            {
                float progress = i / 3F;
                float length = spokeRadius[spoke] * progress;

                this.splatBuffer[i].set(
                    base.x + (right.x * cos + up.x * sin) * length + normal.x * radius * 0.06F * progress,
                    base.y + (right.y * cos + up.y * sin) * length + normal.y * radius * 0.06F * progress,
                    base.z + (right.z * cos + up.z * sin) * length + normal.z * radius * 0.06F * progress
                );
            }

            this.appendSimpleTube(builder, model, this.splatBuffer, 4, thickness, color);
        }

        for (int ring = 1; ring <= SPLAT_RINGS; ring++)
        {
            float ringProgress = ring / (float) (SPLAT_RINGS + 1);
            int count = SPLAT_SPOKES + 1;

            for (int spoke = 0; spoke < count; spoke++)
            {
                int index = spoke % SPLAT_SPOKES;
                float angle = index / (float) SPLAT_SPOKES * TWO_PI;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                float length = spokeRadius[index] * ringProgress;

                this.splatBuffer[spoke].set(
                    base.x + (right.x * cos + up.x * sin) * length + normal.x * radius * 0.06F * ringProgress,
                    base.y + (right.y * cos + up.y * sin) * length + normal.y * radius * 0.06F * ringProgress,
                    base.z + (right.z * cos + up.z * sin) * length + normal.z * radius * 0.06F * ringProgress
                );
            }

            this.appendSimpleTube(builder, model, this.splatBuffer, count, thickness * 0.85F, color);
        }
    }

    /**
     * A tube of constant radius through the first {@code count} points of the buffer.
     * The main line has strands and taper to worry about; the splat and the editor
     * marker just need thin, even thread.
     */
    private void appendSimpleTube(BufferBuilder builder, Matrix4f model, Vector3f[] points, int count, float radius, Color color)
    {
        if (count < 2)
        {
            return;
        }

        Vector3f[] slice = points;

        if (count != points.length)
        {
            this.frameBuffer = ensureVectors(this.frameBuffer, count);

            for (int i = 0; i < count; i++)
            {
                this.frameBuffer[i].set(points[i]);
            }

            slice = this.frameBuffer;
        }

        this.computeFrames(slice);

        for (int i = 0; i < count - 1; i++)
        {
            Vector3f pointA = slice[i];
            Vector3f pointB = slice[i + 1];
            Vector3f normalA = this.frameNormals[i];
            Vector3f binormalA = this.frameBinormals[i];
            Vector3f normalB = this.frameNormals[i + 1];
            Vector3f binormalB = this.frameBinormals[i + 1];

            for (int side = 0; side < RING_SEGMENTS; side++)
            {
                float cos1 = RING_COS[side];
                float sin1 = RING_SIN[side];
                float cos2 = RING_COS[side + 1];
                float sin2 = RING_SIN[side + 1];

                this.ringPoint(this.ringA, pointA, normalA, binormalA, cos1, sin1, radius);
                this.ringPoint(this.ringB, pointB, normalB, binormalB, cos1, sin1, radius);
                this.ringPoint(this.ringC, pointB, normalB, binormalB, cos2, sin2, radius);
                this.ringPoint(this.ringD, pointA, normalA, binormalA, cos2, sin2, radius);

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

    /**
     * The editor's stand-in for a holstered shooter. With the web hidden there is
     * nothing to grab or even see, so an editor viewport gets a small cross on the
     * start point (it follows the sliders live) and a dashed ghost of the shot to
     * where the end point is aimed. Never drawn in the world - a finished scene must
     * not have editor furniture in it.
     */
    private void renderShooterMarker(FormRenderingContext context)
    {
        if (!this.form.showAnchor.get() || !(context.modelRenderer || context.ui) || context.isPicking())
        {
            return;
        }

        Vector3f start = new Vector3f(this.form.start.get());
        Vector3f end = new Vector3f(this.form.end.get());
        Matrix4f model = new Matrix4f(context.stack.peek().getPositionMatrix());
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Color color = this.form.color.get().copy();
        Color cross = new Color(color.r, color.g, color.b, 0.9F);
        Color ghost = new Color(color.r, color.g, color.b, 0.28F);
        float size = 0.09F;
        float thread = Math.max(0.006F, this.form.thickness.get() * 0.5F);

        this.splatBuffer = ensureVectors(this.splatBuffer, Math.max(4, SPLAT_SPOKES + 1));

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int axis = 0; axis < 3; axis++)
        {
            this.splatBuffer[0].set(start);
            this.splatBuffer[1].set(start);
            this.splatBuffer[0].setComponent(axis, start.get(axis) - size);
            this.splatBuffer[1].setComponent(axis, start.get(axis) + size);

            this.appendSimpleTube(builder, model, this.splatBuffer, 2, thread, cross);
        }

        /* Dashed preview of where the shot will go. */
        int dashes = 10;

        for (int dash = 0; dash < dashes; dash++)
        {
            float from = dash / (float) dashes;
            float to = from + 0.55F / dashes;

            this.splatBuffer[0].set(start).lerp(end, from);
            this.splatBuffer[1].set(start).lerp(end, to);

            this.appendSimpleTube(builder, model, this.splatBuffer, 2, thread * 0.55F, ghost);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
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

    /**
     * Move the web's own clock to this frame and say what kind of move it was. The
     * renderer used to read the raw entity age every time it needed a timestamp,
     * which broke in an editor in two ways: paused playback still hands out partial
     * ticks that swing back and forth inside one tick (the rope read that as time
     * running backwards and reset itself every single frame), and dragging the
     * playhead moves the age by hundreds of ticks at once. The clock here only ever
     * moves forwards during playback, stands perfectly still while paused, and
     * reports a scrub so the rope can be re-settled instead of dropped from scratch.
     */
    private void advanceClock(PhysicsState state, double time)
    {
        if (state.lastTime == Double.NEGATIVE_INFINITY)
        {
            state.lastTime = time;
            state.clock = time;
            state.timeStatus = TIME_NORMAL;
            state.timeDelta = 0D;

            return;
        }

        double delta = time - state.lastTime;

        if (delta < 0D && delta > -HOLD_TOLERANCE)
        {
            /* Paused: hold everything, and remember the furthest point reached so
             * resuming does not replay the fraction of a tick we sat on. */
            state.timeStatus = TIME_HOLD;
            state.timeDelta = 0D;

            return;
        }

        if (delta <= -HOLD_TOLERANCE || delta > MAX_TICK_STEP)
        {
            state.timeStatus = TIME_JUMP;
            state.timeDelta = delta;
            state.lastTime = time;
            state.clock += delta;

            return;
        }

        state.timeStatus = TIME_NORMAL;
        state.timeDelta = delta;
        state.lastTime = time;
        state.clock += delta;
    }

    /**
     * Put the rope where it would have come to rest, in one go. Scrubbing a film to
     * an arbitrary tick cannot replay everything that happened before it (the hand
     * that holds the web moved along a path we no longer have), but showing the line
     * snap straight and then visibly fall - which is what a bare reset looked like -
     * is the worst of both worlds. Instead the solver is run forward here, silently,
     * until the rope hangs the way it should at that anchor.
     */
    private void settle(PhysicsState state, FormRenderingContext context, Vector3f start, Vector3f endpoint)
    {
        Vector3f target = state.anchorMode == WebForm.ANCHOR_LOCKED ? new Vector3f(state.lockedEnd) : endpoint;

        state.reset(state.segments, state.anchorMode, state.simulation, start, target, this.form.length.get(), this.form.sag.get());
        state.accumulator = 0F;

        int iterations = MathUtils.clamp(this.form.iterations.get(), 1, 12);

        for (int i = 0; i < SETTLE_STEPS; i++)
        {
            state.snapshot();
            this.integrate(state, state.step++);
            this.dampSegments(state);
            this.applyPins(state, start, target);
            this.solveConstraints(state, context, iterations, start, target);
        }

        /* Land it at rest: whatever speed the settling left behind must not be
         * released the moment playback resumes. */
        for (int i = 0; i < state.points.length; i++)
        {
            state.previous[i].set(state.points[i]);
            state.past[i].set(state.points[i]);
        }
    }

    private void updateSimulation(PhysicsState state, FormRenderingContext context, double time, Vector3f start, Vector3f endpoint)
    {
        if (state.timeStatus == TIME_JUMP)
        {
            this.settle(state, context, start, endpoint);

            return;
        }

        double delta = state.timeDelta;

        if (state.timeStatus == TIME_HOLD || this.form.paused.get())
        {
            /* Frozen exactly where it stands - the ends still follow their anchors,
             * so a paused rope stays attached to a hand that the editor moves, and
             * the previous positions move with them so resuming does not fire the
             * whole pause off as one enormous velocity. */
            state.accumulator = 0F;
            this.applyPins(state, start, endpoint);
            state.snapshot();

            return;
        }

        /* The speed multiplier stretches the simulation clock: at 1 the rope runs on
         * game time, at 3 it swings three times as fast without touching gravity or
         * the constraint solve, so the motion stays the same shape. */
        float speed = MathUtils.clamp(this.form.speed.get(), 0F, WebForm.MAX_SPEED);

        state.accumulator += (float) (delta * speed);

        this.applyReel(state, delta);

        int steps = 0;

        while (state.accumulator >= 1F && steps < MAX_STEPS_PER_FRAME)
        {
            state.snapshot();
            this.integrate(state, state.step++);
            this.dampSegments(state);
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
     * Winch the attached line in (or pay it out) while it hangs. This is the swing
     * itself in the films: the line does not just hold, it shortens and lifts the
     * rider. Only the rest length changes, so the solver does the pulling and the
     * rope keeps behaving like a rope.
     */
    private void applyReel(PhysicsState state, double delta)
    {
        float reel = this.form.reel.get();

        if (reel == 0F || state.points.length < 2)
        {
            return;
        }

        /* Reeling a rope whose far end is not tied to anything just drags the tip
         * around, so it is limited to lines that actually hold on to something. */
        if (state.anchorMode == WebForm.ANCHOR_FREE)
        {
            return;
        }

        float seconds = (float) (delta / 20D);
        float base = Math.max(MIN_DISTANCE, state.restLength + state.reeled);

        state.reeled = MathUtils.clamp(state.reeled + reel * seconds, -512F, base);
        state.syncRest(state.lengthSetting, state.sagSetting, state.points[0], state.points[state.points.length - 1]);
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

        /* Floored: a 300 kg load would otherwise take 99% of every correction and
         * hand the whole error to its light neighbour, which the solver answers with
         * a violent shudder. The floor keeps heavy heavy and the rope stable. */
        return Math.max(MIN_INVERSE_MASS, POINT_MASS / (POINT_MASS + this.pointMass[index]));
    }

    private void integrate(PhysicsState state, long step)
    {
        int last = state.points.length - 1;
        int end = state.anchorMode == WebForm.ANCHOR_FREE ? last + 1 : last;
        float damping = 1F - MathUtils.clamp(this.form.damping.get(), 0F, 1F) * DAMPING_SCALE;
        float gravity = Math.max(0F, this.form.gravity.get()) * GRAVITY_SCALE;
        Vector3f wind = this.form.wind.get();
        float windSpeed = Math.max(0F, this.form.windSpeed.get());

        /* Gusts are a modulation OF the wind, not a force of their own. They used to
         * blow at full strength with the wind vector at zero, which meant the default
         * web was permanently stirred: a hanging body never came to rest and the rope
         * shivered in place. No wind, no gusts. */
        float noiseAmount = MathUtils.clamp(this.form.windNoise.get(), 0F, 1F) * wind.length();

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
            /* A slow phase shift along the rope, not a different gust per point: with
             * the old spacing neighbouring points were pushed in opposite directions
             * on the same tick, which reads as shaking rather than as wind. */
            float phase = step * 0.08F * windSpeed + i * 0.28F;
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

    /**
     * Take the speed difference out of neighbouring points along the line between
     * them. A rope swinging as one is untouched (its points all move together), while
     * the buzzing the constraint solver leaves behind - segments sawing back and
     * forth against each other, which a hanging body magnifies into a shake - dies
     * within a few ticks. Verlet keeps its speed in the previous position, so the
     * correction is written there.
     */
    private void dampSegments(PhysicsState state)
    {
        int last = state.points.length - 1;

        if (last < 1)
        {
            return;
        }

        boolean pinEnd = state.anchorMode != WebForm.ANCHOR_FREE;

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

            dx /= distance;
            dy /= distance;
            dz /= distance;

            Vector3f previousFirst = state.previous[i];
            Vector3f previousSecond = state.previous[i + 1];
            float relative =
                  ((second.x - previousSecond.x) - (first.x - previousFirst.x)) * dx
                + ((second.y - previousSecond.y) - (first.y - previousFirst.y)) * dy
                + ((second.z - previousSecond.z) - (first.z - previousFirst.z)) * dz;

            if (relative == 0F)
            {
                continue;
            }

            boolean firstPinned = i == 0;
            boolean secondPinned = i + 1 == last && pinEnd;

            if (firstPinned && secondPinned)
            {
                continue;
            }

            float impulse = relative * SEGMENT_DAMPING * (firstPinned || secondPinned ? 1F : 0.5F);

            if (!firstPinned)
            {
                previousFirst.sub(dx * impulse, dy * impulse, dz * impulse);
            }

            if (!secondPinned)
            {
                previousSecond.add(dx * impulse, dy * impulse, dz * impulse);
            }
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

                /* One pass may only take out so much: a rope yanked far past its rest
                 * length (a landing shot, a teleport, a heavy part dropped on it) would
                 * otherwise be thrown back harder than it was pulled, and ring for
                 * seconds afterwards. */
                float limit = segmentLength * MAX_CORRECTION;

                error = MathUtils.clamp(error, -limit, limit);

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

        /**
         * The web's own clock. Unlike the entity's age it never runs backwards on a
         * paused frame, which is what everything timed (the shot above all) reads.
         */
        public double clock;
        public int timeStatus = TIME_NORMAL;
        public double timeDelta;

        public float accumulator;
        public long step;

        /* Web shooter state of the current shot */
        public int shotPhase = SHOT_IDLE;
        public int shotMode = WebForm.SHOT_ANCHORED;
        public double fireTime;
        public double landTime = Double.NEGATIVE_INFINITY;
        public float shotTravelled;
        public boolean shotResetPending;
        public final Vector3f shotOrigin = new Vector3f();
        public final Vector3f shotTarget = new Vector3f();

        public int segments;
        public int anchorMode;
        public boolean simulation;
        public float lengthSetting;
        public float sagSetting;
        public float restLength;

        /** Blocks the line has been winched in; kept apart so the settings stay authoritative. */
        public float reeled;

        /** Only a rope that does not exist yet, or one that changed solver mode, is rebuilt. */
        public boolean needsReset(boolean simulation)
        {
            return this.points.length < 2 || this.simulation != simulation;
        }

        /**
         * Follow the length and sag settings without rebuilding anything. The rest
         * length is the setting or the span between the anchors, whichever is longer,
         * minus whatever has been reeled in so far.
         */
        public void syncRest(float length, float sag, Vector3f start, Vector3f end)
        {
            this.lengthSetting = length;
            this.sagSetting = sag;

            Vector3f span = this.anchorMode == WebForm.ANCHOR_FREE ? this.initialEnd : end;
            float base = Math.max(MIN_DISTANCE, Math.max(length, start.distance(span)));
            float minimum = Math.max(MIN_DISTANCE, this.points.length * 0.02F);

            this.restLength = MathUtils.clamp(base - this.reeled, minimum, 512F);
        }

        /** Swap which ends are held, keeping the rope exactly where it hangs. */
        public void setAnchorMode(int anchorMode, Vector3f end)
        {
            this.anchorMode = anchorMode;

            if (anchorMode == WebForm.ANCHOR_LOCKED)
            {
                this.lockedEnd.set(end);
            }
            else if (anchorMode == WebForm.ANCHOR_FREE)
            {
                this.initialEnd.set(end);
            }
        }

        /**
         * Change the point count by sampling the rope that is already there, instead
         * of throwing it away. Speed is carried over with it, so a web being animated
         * through a segment change keeps swinging.
         */
        public void resample(int count)
        {
            if (count < 2 || this.points.length < 2)
            {
                return;
            }

            Vector3f[] newPoints = new Vector3f[count];
            Vector3f[] newPrevious = new Vector3f[count];
            Vector3f[] newPast = new Vector3f[count];
            Vector3f[] newRendered = new Vector3f[count];
            int old = this.points.length;

            for (int i = 0; i < count; i++)
            {
                float position = i / (float) (count - 1) * (old - 1);
                int a = MathUtils.clamp((int) position, 0, old - 1);
                int b = Math.min(a + 1, old - 1);
                float blend = position - a;

                newPoints[i] = new Vector3f(this.points[a]).lerp(this.points[b], blend);
                newPrevious[i] = new Vector3f(this.previous[a]).lerp(this.previous[b], blend);
                newPast[i] = new Vector3f(newPoints[i]);
                newRendered[i] = new Vector3f(newPoints[i]);
            }

            this.points = newPoints;
            this.previous = newPrevious;
            this.past = newPast;
            this.rendered = newRendered;
            this.segments = count;
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
            this.reeled = 0F;
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

        /**
         * Kick the rope the moment a shot lands. A stuck line snaps taut and rings
         * like a plucked string; a shot into thin air keeps the speed it was flying
         * at, so the tip overshoots and only then droops. Both are written as a
         * difference between the current and previous positions - the Verlet solver
         * reads that difference as velocity.
         */
        public void applyShotLanding(Vector3f start, Vector3f end, float shotSpeed, boolean released)
        {
            int last = this.points.length - 1;

            if (last < 1)
            {
                return;
            }

            Vector3f direction = new Vector3f(end).sub(start);
            float distance = direction.length();

            if (distance <= MIN_DISTANCE)
            {
                return;
            }

            direction.div(distance);

            if (released)
            {
                /* Momentum of the flight, strongest at the tip. */
                float speed = Math.min(Math.max(0.01F, shotSpeed), MAX_STEP) * 0.5F;

                for (int i = 1; i <= last; i++)
                {
                    float progress = i / (float) last;

                    this.previous[i].set(this.points[i]).sub(
                        direction.x * speed * progress,
                        direction.y * speed * progress,
                        direction.z * speed * progress
                    );
                }

                return;
            }

            /* A transverse nudge along the line: one half wave, tiny but visible. */
            Vector3f side = new Vector3f(Math.abs(direction.y) < 0.9F ? 0F : 1F, Math.abs(direction.y) < 0.9F ? 1F : 0F, 0F);

            side.cross(direction);

            if (side.lengthSquared() <= MIN_DISTANCE * MIN_DISTANCE)
            {
                return;
            }

            side.normalize();

            float amplitude = Math.min(0.12F, distance * 0.02F);

            for (int i = 1; i < last; i++)
            {
                float progress = i / (float) last;
                float wave = (float) Math.sin(Math.PI * progress) * amplitude;

                this.previous[i].set(this.points[i]).sub(side.x * wave, side.y * wave, side.z * wave);
            }
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
