package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Vector3f;

/**
 * Procedural Spider-Man web line. The form is intentionally independent from a model asset: place it
 * as a body part on a model bone (a wrist is the usual choice), animate its end point, or let the
 * client-side rope solver carry the line between the moving shooter and a world-locked anchor.
 */
public class WebForm extends Form
{
    public static final int ANCHOR_FOLLOW = 0;
    public static final int ANCHOR_LOCKED = 1;
    public static final int ANCHOR_FREE = 2;

    public final ValueVector3f start = new ValueVector3f("start", new Vector3f(0F, 0F, 0F));
    public final ValueVector3f end = new ValueVector3f("end", new Vector3f(0F, 5F, 0F));
    public final ValueInt anchorMode = new ValueInt("anchor_mode", ANCHOR_FOLLOW, ANCHOR_FOLLOW, ANCHOR_FREE);
    public final ValueFloat length = new ValueFloat("length", 0F, 0F, 256F);

    public final ValueInt segments = new ValueInt("segments", 16, 2, 64);
    public final ValueFloat thickness = new ValueFloat("thickness", 0.025F, 0.001F, 0.5F);
    public final ValueFloat taper = new ValueFloat("taper", 0.15F, 0F, 1F);
    public final ValueFloat sag = new ValueFloat("sag", 0.12F, 0F, 16F);
    public final ValueInt strands = new ValueInt("strands", 1, 1, 3);
    public final ValueFloat strandSpread = new ValueFloat("strand_spread", 0.012F, 0F, 0.2F);

    public final ValueBoolean physics = new ValueBoolean("physics", true);
    public final ValueBoolean paused = new ValueBoolean("paused", false);
    public final ValueFloat gravity = new ValueFloat("gravity", 0.45F, 0F, 2F);
    public final ValueFloat damping = new ValueFloat("damping", 0.08F, 0F, 1F);
    public final ValueFloat stiffness = new ValueFloat("stiffness", 0.82F, 0F, 1F);
    public final ValueVector3f wind = new ValueVector3f("wind", new Vector3f());
    public final ValueFloat windNoise = new ValueFloat("wind_noise", 0.15F, 0F, 1F);
    public final ValueFloat windSpeed = new ValueFloat("wind_speed", 1F, 0F, 4F);
    public final ValueBoolean collisions = new ValueBoolean("collisions", false);
    public final ValueInt iterations = new ValueInt("iterations", 6, 1, 12);

    public final ValueColor color = new ValueColor("color", Color.white());

    public WebForm()
    {
        super();

        this.add(this.start);
        this.add(this.end);
        this.add(this.anchorMode);
        this.add(this.length);
        this.add(this.segments);
        this.add(this.thickness);
        this.add(this.taper);
        this.add(this.sag);
        this.add(this.strands);
        this.add(this.strandSpread);
        this.add(this.physics);
        this.add(this.paused);
        this.add(this.gravity);
        this.add(this.damping);
        this.add(this.stiffness);
        this.add(this.wind);
        this.add(this.windNoise);
        this.add(this.windSpeed);
        this.add(this.collisions);
        this.add(this.iterations);
        this.add(this.color);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return "Spider Web";
    }
}
