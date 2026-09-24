package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;

public class BodyPart extends ValueGroup
{
    /**
     * The frame a part with no bone is given: the parent form's own axes turned by 180&deg; about Y
     * (see the {@code renderBodyParts} fallback). Every bone matrix carries that same turn baked in
     * by {@code ModelInstance.captureMatrices}, so this is also what is left of a bone once its own
     * rotation is dropped — which is why a part reads the same way whether its rotation is not
     * inherited or its bone is missing.
     */
    private static final Matrix4f NO_BONE = new Matrix4f().rotateY((float) Math.PI);

    private Form form;

    /** Default mass used when the weight toggle is switched on without an estimate. */
    public static final float DEFAULT_WEIGHT = 60F;

    public static final float MAX_WEIGHT = 10000F;

    public final ValueTransform transform = new ValueTransform("transform", new Transform());
    public final ValueString bone = new ValueString("bone", "");

    /**
     * Which bone of THIS part's own form is put onto the attachment point. Empty
     * means the form's origin, the way it has always worked; naming a bone (a hand,
     * say) makes that bone the contact point instead.
     */
    public final ValueString attachBone = new ValueString("attach_bone", "");

    /**
     * Optional mass, in kilograms. Off by default - the part then weighs nothing
     * and the parent's physics behave exactly as before. Switched on, a rope the
     * part hangs from carries the load: it stretches toward the weight, and heavy
     * parts keep their momentum instead of being damped away.
     */
    public final ValueBoolean weightEnabled = new ValueBoolean("weight_enabled", false);
    public final ValueFloat weight = new ValueFloat("weight", DEFAULT_WEIGHT, 0F, MAX_WEIGHT);

    public final ValueBoolean useTarget = new ValueBoolean("useTarget", false);

    /**
     * Which components of the attachment bone's frame the part rides. All three on (the default) is
     * the plain behaviour: the bone's matrix is taken whole. Turning one off drops that component
     * from the bone and leaves the part with the parent form's own — a sight that follows the hand
     * without turning with it, a label that follows the head without tilting, a form that keeps its
     * size on a bone the pose stretched.
     */
    public final ValueBoolean inheritPosition = new ValueBoolean("inheritPosition", true);
    public final ValueBoolean inheritRotation = new ValueBoolean("inheritRotation", true);
    public final ValueBoolean inheritScale = new ValueBoolean("inheritScale", true);

    private IEntity entity = new StubEntity();

    public BodyPart(String id)
    {
        super(id);

        this.add(this.transform);
        this.add(this.bone);
        this.add(this.attachBone);
        this.add(this.weightEnabled);
        this.add(this.weight);
        this.add(this.useTarget);
        this.add(this.inheritPosition);
        this.add(this.inheritRotation);
        this.add(this.inheritScale);
    }

    public Form getForm()
    {
        return this.form;
    }

    public boolean inheritsWholeBone()
    {
        return this.inheritPosition.get() && this.inheritRotation.get() && this.inheritScale.get();
    }

    /**
     * The attachment bone's matrix with the components the part doesn't inherit taken from
     * {@link #NO_BONE} instead. Returns the matrix itself when there is nothing to take out, and
     * never modifies the input: the bone matrices are a shared per-frame cache read by every part
     * hanging off that bone.
     */
    public Matrix4f filterBoneMatrix(Matrix4f matrix)
    {
        if (matrix == null || this.inheritsWholeBone())
        {
            return matrix;
        }

        return Matrices.compose(
            this.inheritPosition.get() ? matrix : NO_BONE,
            this.inheritRotation.get() ? matrix : NO_BONE,
            this.inheritScale.get() ? matrix : NO_BONE
        );
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public BodyPartManager getManager()
    {
        return this.parent instanceof BodyPartManager parts ? parts : null;
    }

    public void setForm(Form form)
    {
        this.preNotify();
        this.setInternalForm(form);
        this.postNotify();
    }

    private void setInternalForm(Form form)
    {
        if (this.form != null)
        {
            this.remove(this.form);
        }

        this.form = form;

        if (this.form != null)
        {
            form.setId("form");
            this.add(this.form);
        }
    }

    /**
     * The entity the nested form is rendered and updated with: the target itself when the part mirrors it,
     * otherwise the part's own neutral entity, given the target's world first.
     */
    public IEntity getRenderEntity(IEntity target)
    {
        if (this.useTarget.get())
        {
            return target;
        }

        this.syncWorld(target);

        return this.entity;
    }

    /**
     * A part that doesn't use the target keeps its own entity state — pose flags, limb swing, velocity, and
     * its own age — that's the point of {@link #useTarget}. The world is not state though: the part hangs on
     * the target, so it is in the target's world either way, and it was simply never told which one (the
     * part's entity is built with the worldless constructor and nothing ever set it). Bone physics reads the
     * world off the entity to collide against, so a part's chains could never collide with anything.
     *
     * <p>Only the world. The age deliberately stays the part's own counter, advanced by {@link #update}: a
     * part is rendered by several paths in one frame that keep different clocks (the editor's preview, list
     * thumbnails whose entity never ages), so deriving its age from whoever renders it makes the age jump
     * between their clocks — which the physics solver reads as a huge or negative tick delta and re-seeds
     * on, freezing every chain. Its own counter is robust precisely because it belongs to nobody else.</p>
     */
    private void syncWorld(IEntity target)
    {
        if (target == null || target == this.entity)
        {
            return;
        }

        this.entity.setWorld(target.getWorld());
    }

    public void update(IEntity target)
    {
        this.syncWorld(target);

        if (this.form != null)
        {
            this.form.update(this.useTarget.get() ? target : this.entity);
        }

        this.entity.update();
    }

    public BodyPart copy()
    {
        BodyPart part = new BodyPart(this.id);

        part.fromData(this.toData());

        return part;
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        if (data.isMap())
        {
            MapType map = data.asMap();
            Form form = map.has("form") ? FormUtils.fromData(map.getMap("form")) : null;

            this.setInternalForm(form);
        }
    }
}