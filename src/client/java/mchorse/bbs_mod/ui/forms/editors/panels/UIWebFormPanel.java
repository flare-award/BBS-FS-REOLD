package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.WebForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.WebFormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

public class UIWebFormPanel extends UIFormPanel<WebForm>
{
    public UITrackpad startX;
    public UITrackpad startY;
    public UITrackpad startZ;
    public UITrackpad endX;
    public UITrackpad endY;
    public UITrackpad endZ;
    public UICirculate anchorMode;
    public UITrackpad length;

    public UITrackpad segments;
    public UITrackpad thickness;
    public UITrackpad taper;
    public UITrackpad sag;
    public UITrackpad strands;
    public UITrackpad strandSpread;

    public UIToggle physics;
    public UIToggle paused;
    public UITrackpad speed;
    public UITrackpad gravity;
    public UITrackpad damping;
    public UITrackpad stiffness;
    public UITrackpad windX;
    public UITrackpad windY;
    public UITrackpad windZ;
    public UITrackpad windNoise;
    public UITrackpad windSpeed;
    public UIToggle collisions;
    public UITrackpad iterations;

    public UIToggle shooter;
    public UICirculate shotMode;
    public UIToggle fire;
    public UITrackpad shotSpeed;
    public UITrackpad shotTail;
    public UIToggle splat;
    public UITrackpad splatSize;
    public UITrackpad dissolve;
    public UITrackpad reel;
    public UIToggle showAnchor;

    public UIColor color;
    public UIButton reset;
    public UIButton tutorial;

    public UIWebFormPanel(UIForm editor)
    {
        super(editor);

        this.startX = this.createVectorInput(true, 0);
        this.startY = this.createVectorInput(true, 1);
        this.startZ = this.createVectorInput(true, 2);
        this.endX = this.createVectorInput(false, 0);
        this.endY = this.createVectorInput(false, 1);
        this.endZ = this.createVectorInput(false, 2);

        this.anchorMode = new UICirculate((b) ->
        {
            if (this.form != null)
            {
                this.form.anchorMode.set(b.getValue());
                this.resetSimulation();
            }
        });
        this.anchorMode.addLabel(UIKeys.FORMS_EDITORS_WEB_ANCHOR_FOLLOW);
        this.anchorMode.addLabel(UIKeys.FORMS_EDITORS_WEB_ANCHOR_LOCKED);
        this.anchorMode.addLabel(UIKeys.FORMS_EDITORS_WEB_ANCHOR_FREE);

        this.length = new UITrackpad((v) -> this.form.length.set(v.floatValue())).limit(0D, 256D).increment(0.05D);
        this.segments = new UITrackpad((v) ->
        {
            this.form.segments.set(v.intValue());
            this.resetSimulation();
        }).limit(2D, 64D).integer();
        this.thickness = new UITrackpad((v) -> this.form.thickness.set(v.floatValue())).limit(0.001D, 0.5D).increment(0.001D);
        this.taper = new UITrackpad((v) -> this.form.taper.set(v.floatValue())).limit(0D, 1D).increment(0.01D);
        this.sag = new UITrackpad((v) -> this.form.sag.set(v.floatValue())).limit(0D, 16D).increment(0.01D);
        this.strands = new UITrackpad((v) -> this.form.strands.set(v.intValue())).limit(1D, 3D).integer();
        this.strandSpread = new UITrackpad((v) -> this.form.strandSpread.set(v.floatValue())).limit(0D, 0.2D).increment(0.001D);

        this.physics = new UIToggle(UIKeys.FORMS_EDITORS_WEB_PHYSICS, (b) ->
        {
            this.form.physics.set(b.getValue());
            this.resetSimulation();
        });
        this.paused = new UIToggle(UIKeys.FORMS_EDITORS_WEB_PAUSED, (b) -> this.form.paused.set(b.getValue()));
        this.speed = new UITrackpad((v) -> this.form.speed.set(v.floatValue())).limit(0D, WebForm.MAX_SPEED).increment(0.05D);
        this.gravity = new UITrackpad((v) -> this.form.gravity.set(v.floatValue())).limit(0D, 2D).increment(0.01D);
        this.damping = new UITrackpad((v) -> this.form.damping.set(v.floatValue())).limit(0D, 1D).increment(0.01D);
        this.stiffness = new UITrackpad((v) -> this.form.stiffness.set(v.floatValue())).limit(0D, 1D).increment(0.01D);
        this.windX = this.createWindInput(0);
        this.windY = this.createWindInput(1);
        this.windZ = this.createWindInput(2);
        this.windNoise = new UITrackpad((v) -> this.form.windNoise.set(v.floatValue())).limit(0D, 1D).increment(0.01D);
        this.windSpeed = new UITrackpad((v) -> this.form.windSpeed.set(v.floatValue())).limit(0D, 4D).increment(0.05D);
        this.collisions = new UIToggle(UIKeys.FORMS_EDITORS_WEB_COLLISIONS, (b) -> this.form.collisions.set(b.getValue()));
        this.iterations = new UITrackpad((v) -> this.form.iterations.set(v.intValue())).limit(1D, 12D).integer();

        this.shooter = new UIToggle(UIKeys.FORMS_EDITORS_WEB_SHOOTER, (b) ->
        {
            this.form.shooter.set(b.getValue());
            this.resetSimulation();
        });
        this.shooter.tooltip(UIKeys.FORMS_EDITORS_WEB_SHOOTER_TOOLTIP);

        this.shotMode = new UICirculate((b) ->
        {
            if (this.form != null)
            {
                this.form.shotMode.set(b.getValue());
                this.resetSimulation();
            }
        });
        this.shotMode.addLabel(UIKeys.FORMS_EDITORS_WEB_SHOT_ANCHORED);
        this.shotMode.addLabel(UIKeys.FORMS_EDITORS_WEB_SHOT_AIR);
        this.shotMode.addLabel(UIKeys.FORMS_EDITORS_WEB_SHOT_SINGLE);
        this.shotMode.tooltip(UIKeys.FORMS_EDITORS_WEB_SHOT_MODE_TOOLTIP);

        this.fire = new UIToggle(UIKeys.FORMS_EDITORS_WEB_FIRE, (b) -> this.form.fire.set(b.getValue()));
        this.fire.tooltip(UIKeys.FORMS_EDITORS_WEB_FIRE_TOOLTIP);

        this.shotSpeed = new UITrackpad((v) -> this.form.shotSpeed.set(v.floatValue())).limit(0.1D, 32D).increment(0.1D);
        this.shotTail = new UITrackpad((v) -> this.form.shotTail.set(v.floatValue())).limit(0.1D, 32D).increment(0.1D);
        this.splat = new UIToggle(UIKeys.FORMS_EDITORS_WEB_SPLAT, (b) -> this.form.splat.set(b.getValue()));
        this.splatSize = new UITrackpad((v) -> this.form.splatSize.set(v.floatValue())).limit(0.05D, 4D).increment(0.05D);
        this.dissolve = new UITrackpad((v) -> this.form.dissolve.set(v.floatValue())).limit(0D, 600D).increment(0.5D);
        this.dissolve.tooltip(UIKeys.FORMS_EDITORS_WEB_DISSOLVE_TOOLTIP);
        this.reel = new UITrackpad((v) -> this.form.reel.set(v.floatValue())).limit(-16D, 16D).increment(0.05D);
        this.reel.tooltip(UIKeys.FORMS_EDITORS_WEB_REEL_TOOLTIP);
        this.showAnchor = new UIToggle(UIKeys.FORMS_EDITORS_WEB_SHOW_ANCHOR, (b) -> this.form.showAnchor.set(b.getValue()));
        this.showAnchor.tooltip(UIKeys.FORMS_EDITORS_WEB_SHOW_ANCHOR_TOOLTIP);

        this.color = new UIColor((value) -> this.form.color.set(Color.rgba(value))).direction(Direction.LEFT).withAlpha();
        this.reset = new UIButton(UIKeys.FORMS_EDITORS_WEB_RESET, (b) -> this.resetSimulation());

        /* The web has far more moving parts than the other forms, so it carries its
         * own manual instead of hoping every knob is guessable from its tooltip. */
        this.tutorial = new UIButton(UIKeys.FORMS_EDITORS_WEB_TUTORIAL, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIWebTutorialOverlayPanel(), 0.8F, 0.85F);
        });
        this.tutorial.tooltip(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_TOOLTIP);

        UISection anchors = this.section(UIKeys.FORMS_EDITORS_WEB_SECTION_ANCHORS, "web.anchors", true);
        anchors.fields.add(
            UI.label(UIKeys.FORMS_EDITORS_WEB_START),
            UI.row(this.startX, this.startY, this.startZ),
            UI.label(UIKeys.FORMS_EDITORS_WEB_END),
            UI.row(this.endX, this.endY, this.endZ),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_ANCHOR_MODE, this.anchorMode),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_LENGTH, this.length)
        );

        UISection shape = this.section(UIKeys.FORMS_EDITORS_WEB_SECTION_SHAPE, "web.shape", true);
        shape.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SEGMENTS, this.segments),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_THICKNESS, this.thickness),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_TAPER, this.taper),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SAG, this.sag),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_STRANDS, this.strands),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_STRAND_SPREAD, this.strandSpread)
        );

        UISection dynamics = this.section(UIKeys.FORMS_EDITORS_WEB_SECTION_PHYSICS, "web.physics", true);
        dynamics.fields.add(
            this.physics,
            this.paused,
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SPEED, this.speed),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_GRAVITY, this.gravity),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_DAMPING, this.damping),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_STIFFNESS, this.stiffness),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_ITERATIONS, this.iterations),
            this.collisions
        );

        UISection wind = this.section(UIKeys.FORMS_EDITORS_WEB_SECTION_WIND, "web.wind", true);
        wind.fields.add(
            UI.label(UIKeys.FORMS_EDITORS_WEB_WIND),
            UI.row(this.windX, this.windY, this.windZ),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_WIND_NOISE, this.windNoise),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_WIND_SPEED, this.windSpeed)
        );

        UISection shooterSection = this.section(UIKeys.FORMS_EDITORS_WEB_SECTION_SHOOTER, "web.shooter", true);
        shooterSection.fields.add(
            this.shooter,
            this.fire,
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SHOT_MODE, this.shotMode),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SHOT_SPEED, this.shotSpeed),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SHOT_TAIL, this.shotTail),
            this.splat,
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_SPLAT_SIZE, this.splatSize),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_DISSOLVE, this.dissolve),
            UI.labelRow(UIKeys.FORMS_EDITORS_WEB_REEL, this.reel),
            this.showAnchor
        );

        UISection material = this.section(UIKeys.FORMS_EDITORS_WEB_SECTION_MATERIAL, "web.material", true);
        material.fields.add(UI.labelRow(UIKeys.FORMS_EDITORS_WEB_COLOR, this.color));

        this.options.add(this.tutorial, anchors, shooterSection, shape, dynamics, wind, material, this.reset);
    }

    private UITrackpad createVectorInput(boolean start, int axis)
    {
        UITrackpad input = new UITrackpad((v) ->
        {
            if (this.form != null)
            {
                this.setComponent(start ? this.form.start : this.form.end, axis, v.floatValue());

                /* Re-aiming by hand re-shoots, so the sliders stay alive while a shot
                 * is out. A shot that is already flying or stuck never follows the
                 * end point on its own - keyframed aim would drag it off the wall. */
                if (this.form.shooter.get())
                {
                    this.resetSimulation();
                }
            }
        });

        input.increment(0.01D);

        return input;
    }

    private UITrackpad createWindInput(int axis)
    {
        UITrackpad input = new UITrackpad((v) ->
        {
            if (this.form != null)
            {
                this.setComponent(this.form.wind, axis, v.floatValue());
            }
        });

        input.limit(-10D, 10D).increment(0.01D);

        return input;
    }

    private void setComponent(ValueVector3f value, int axis, float component)
    {
        BaseValue.edit(value, (vector) ->
        {
            if (axis == 0)
            {
                vector.get().x = component;
            }
            else if (axis == 1)
            {
                vector.get().y = component;
            }
            else
            {
                vector.get().z = component;
            }
        });
    }

    private void resetSimulation()
    {
        if (this.form == null)
        {
            return;
        }

        FormRenderer renderer = FormUtilsClient.getRenderer(this.form);

        if (renderer instanceof WebFormRenderer web)
        {
            web.resetSimulation();
        }
    }

    @Override
    public void startEdit(WebForm form)
    {
        super.startEdit(form);

        this.startX.setValue(form.start.get().x);
        this.startY.setValue(form.start.get().y);
        this.startZ.setValue(form.start.get().z);
        this.endX.setValue(form.end.get().x);
        this.endY.setValue(form.end.get().y);
        this.endZ.setValue(form.end.get().z);
        this.anchorMode.setValue(form.anchorMode.get());
        this.length.setValue(form.length.get());
        this.segments.setValue(form.segments.get());
        this.thickness.setValue(form.thickness.get());
        this.taper.setValue(form.taper.get());
        this.sag.setValue(form.sag.get());
        this.strands.setValue(form.strands.get());
        this.strandSpread.setValue(form.strandSpread.get());
        this.physics.setValue(form.physics.get());
        this.paused.setValue(form.paused.get());
        this.speed.setValue(form.speed.get());
        this.gravity.setValue(form.gravity.get());
        this.damping.setValue(form.damping.get());
        this.stiffness.setValue(form.stiffness.get());
        this.windX.setValue(form.wind.get().x);
        this.windY.setValue(form.wind.get().y);
        this.windZ.setValue(form.wind.get().z);
        this.windNoise.setValue(form.windNoise.get());
        this.windSpeed.setValue(form.windSpeed.get());
        this.collisions.setValue(form.collisions.get());
        this.iterations.setValue(form.iterations.get());
        this.shooter.setValue(form.shooter.get());
        this.fire.setValue(form.fire.get());
        this.shotMode.setValue(form.shotMode.get());
        this.shotSpeed.setValue(form.shotSpeed.get());
        this.shotTail.setValue(form.shotTail.get());
        this.splat.setValue(form.splat.get());
        this.splatSize.setValue(form.splatSize.get());
        this.dissolve.setValue(form.dissolve.get());
        this.reel.setValue(form.reel.get());
        this.showAnchor.setValue(form.showAnchor.get());
        this.color.setColor(form.color.get().getARGBColor());
    }
}
