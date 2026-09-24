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

    /** Upper bound of the simulation speed multiplier. */
    public static final float MAX_SPEED = 8F;

    /* Web shooter: what the fired line does when it reaches its target. */

    /** Sticks to the target and becomes a rope between the hand and that spot. */
    public static final int SHOT_ANCHORED = 0;

    /** Misses on purpose: the tip is released at the target and the line falls. */
    public static final int SHOT_AIR = 1;

    /** A thrown glob: nothing stays in the hand, only the splat on the target. */
    public static final int SHOT_SINGLE = 2;

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

    /**
     * How fast the rope's own clock runs. 1 is game time (the default, so nothing
     * changes for existing webs); higher values swing and settle proportionally
     * faster, which is what fast city swinging needs.
     */
    public final ValueFloat speed = new ValueFloat("speed", 1F, 0F, MAX_SPEED);
    public final ValueFloat gravity = new ValueFloat("gravity", 0.45F, 0F, 2F);
    public final ValueFloat damping = new ValueFloat("damping", 0.08F, 0F, 1F);
    public final ValueFloat stiffness = new ValueFloat("stiffness", 0.82F, 0F, 1F);
    public final ValueVector3f wind = new ValueVector3f("wind", new Vector3f());
    public final ValueFloat windNoise = new ValueFloat("wind_noise", 0.15F, 0F, 1F);
    public final ValueFloat windSpeed = new ValueFloat("wind_speed", 1F, 0F, 4F);
    public final ValueBoolean collisions = new ValueBoolean("collisions", false);
    public final ValueInt iterations = new ValueInt("iterations", 6, 1, 12);

    /**
     * Web shooter mode. Both ends of the web collapse into the start point and the
     * line is not drawn at all until it is fired - the wrist is loaded, nothing
     * hangs off it. The editor viewport marks where that hidden point sits.
     */
    public final ValueBoolean shooter = new ValueBoolean("shooter", false);

    /**
     * The trigger. Flipping it on fires the line from the start point towards the
     * end point; flipping it off holsters the web again, ready for the next shot.
     * It is a plain value, so it keyframes in the film editor like anything else.
     */
    public final ValueBoolean fire = new ValueBoolean("fire", false);

    public final ValueInt shotMode = new ValueInt("shot_mode", SHOT_ANCHORED, SHOT_ANCHORED, SHOT_SINGLE);

    /** Flight speed of the tip, in blocks per tick. */
    public final ValueFloat shotSpeed = new ValueFloat("shot_speed", 3F, 0.1F, 32F);

    /** Length of the flying glob of a single shot - the part that is drawn mid-air. */
    public final ValueFloat shotTail = new ValueFloat("shot_tail", 1.5F, 0.1F, 32F);

    /** Draw the splattered patch of web where the shot lands. */
    public final ValueBoolean splat = new ValueBoolean("splat", true);
    public final ValueFloat splatSize = new ValueFloat("splat_size", 0.45F, 0.05F, 4F);

    /** Seconds the landed web lasts before it fades away; 0 keeps it forever. */
    public final ValueFloat dissolve = new ValueFloat("dissolve", 0F, 0F, 600F);

    /** Blocks per second the attached line reels in (negative pays it out); 0 is off. */
    public final ValueFloat reel = new ValueFloat("reel", 0F, -16F, 16F);

    /** Show the shooter's hidden start point in editor viewports. */
    public final ValueBoolean showAnchor = new ValueBoolean("show_anchor", true);

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
        this.add(this.speed);
        this.add(this.gravity);
        this.add(this.damping);
        this.add(this.stiffness);
        this.add(this.wind);
        this.add(this.windNoise);
        this.add(this.windSpeed);
        this.add(this.collisions);
        this.add(this.iterations);
        this.add(this.shooter);
        this.add(this.fire);
        this.add(this.shotMode);
        this.add(this.shotSpeed);
        this.add(this.shotTail);
        this.add(this.splat);
        this.add(this.splatSize);
        this.add(this.dissolve);
        this.add(this.reel);
        this.add(this.showAnchor);
        this.add(this.color);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return "Spider Web";
    }
}
