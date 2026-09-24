package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIBodyPartEditor extends UIScrollView
{
    public UIButton pick;
    public UIToggle useTarget;
    public UIBonePicker bone;
    public UIBonePicker attachBone;
    public UIToggle weightEnabled;
    public UITrackpad weight;
    public UIElement weightRow;
    public UIPropTransform transform;

    private final UIFormEditor editor;

    private BodyPart part;

    /** The form the body part is attached to — whose bones the picker offers. */
    private Form owner;

    public UIBodyPartEditor(UIFormEditor editor)
    {
        this.editor = editor;

        this.pick = new UIButton(UIKeys.FORMS_EDITOR_PICK_FORM, (b) ->
        {
            UIForms.FormEntry current = this.editor.formsList.getCurrentFirst();

            this.editor.openFormList(current.part.getForm(), (f) ->
            {
                current.part.setForm(FormUtils.copy(f));

                Form partForm = current.part.getForm();

                if (partForm instanceof ModelForm m)
                {
                    m.boneTracks.set(false);
                }

                if (partForm != null && partForm.getFormId().contains("particle"))
                {
                    current.part.useTarget.set(true);

                    this.useTarget.setValue(true);
                }

                this.editor.refreshFormList();
                this.editor.switchEditor(partForm);
            });
        });

        this.useTarget = new UIToggle(UIKeys.FORMS_EDITOR_USE_TARGET, (b) ->
        {
            this.part.useTarget.set(b.getValue());
        });

        this.bone = new UIBonePicker((b) ->
        {
            if (this.part == null)
            {
                return;
            }

            this.part.bone.set(b);
            this.bone.setLabel(this.boneLabel(b));
        });
        this.bone.menu((picker) ->
        {
            if (this.part == null || this.owner == null)
            {
                return;
            }

            ModelInstance model = this.owner instanceof ModelForm modelForm ? ModelFormRenderer.getModel(modelForm) : null;

            if (model != null && model.model != null)
            {
                /* Same visibility rule as FormUtilsClient.getBones: disabled bones
                 * hide unless the pose setting shows them. */
                picker.bones(model.model, BBSSettings.poseShowDisabledBones.get() ? null : model.getDisabledBones());
            }
            else
            {
                /* Bones without a model tree (mob forms' model parts) list flat. */
                List<String> bones = new ArrayList<>(FormUtilsClient.getBones(this.owner));

                bones.sort(String::compareToIgnoreCase);
                picker.list(bones);
            }

            picker.none().set(this.part.bone.get());
        });
        this.bone.viewport(new UIBonePicker.Viewport()
        {
            @Override
            public void startPicking(Consumer<String> callback)
            {
                /* The eyedropper catches bones of the OWNER form (the parent the
                 * part hangs off), not the part's own form being edited. */
                UIBodyPartEditor.this.editor.startBonePicking((pair) ->
                {
                    BodyPart part = UIBodyPartEditor.this.part;

                    callback.accept(pair != null && part != null && part.getManager().getOwner() == pair.a ? pair.b : null);
                });
            }

            @Override
            public void stopPicking()
            {
                UIBodyPartEditor.this.editor.stopBonePicking();
            }
        });

        this.attachBone = new UIBonePicker((b) ->
        {
            if (this.part == null)
            {
                return;
            }

            this.part.attachBone.set(b == null ? "" : b);
            this.attachBone.setLabel(this.attachBoneLabel(b));
        });
        this.attachBone.menu((picker) ->
        {
            if (this.part == null || this.part.getForm() == null)
            {
                return;
            }

            Form partForm = this.part.getForm();
            ModelInstance model = partForm instanceof ModelForm modelForm ? ModelFormRenderer.getModel(modelForm) : null;

            if (model != null && model.model != null)
            {
                picker.bones(model.model, BBSSettings.poseShowDisabledBones.get() ? null : model.getDisabledBones());
            }
            else
            {
                List<String> bones = new ArrayList<>(FormUtilsClient.getBones(partForm));

                bones.sort(String::compareToIgnoreCase);
                picker.list(bones);
            }

            picker.none().set(this.part.attachBone.get());
        });
        this.attachBone.tooltip(UIKeys.FORMS_EDITOR_ATTACH_BONE_TOOLTIP);

        this.weight = new UITrackpad((v) ->
        {
            if (this.part != null)
            {
                this.part.weight.set(v.floatValue());
            }
        }).limit(0D, BodyPart.MAX_WEIGHT).increment(1D);
        this.weight.tooltip(UIKeys.FORMS_EDITOR_WEIGHT_TOOLTIP);
        this.weightRow = UI.labelRow(UIKeys.FORMS_EDITOR_WEIGHT, this.weight);

        this.weightEnabled = new UIToggle(UIKeys.FORMS_EDITOR_WEIGHT_ENABLED, (b) ->
        {
            if (this.part == null)
            {
                return;
            }

            this.part.weightEnabled.set(b.getValue());

            /* Switching it on fills in an estimate, but only while the field was
             * never touched - a value the player typed themselves is never
             * overwritten, since no guess fits every model. */
            if (b.getValue() && this.part.weight.get() == BodyPart.DEFAULT_WEIGHT)
            {
                float estimate = estimateWeight(this.part);

                this.part.weight.set(estimate);
                this.weight.setValue(estimate);
            }

            this.updateWeightVisibility();
        });

        this.transform = new UIPropTransform().callbacks(() -> this.part.transform).barBackground();
        this.transform.enableHotkeys(this.editor::isBodyPartGizmoMode);
        this.transform.hotkeyDrag(() -> this.editor.buildHotkeyDrag(this.transform));

        this.pick.keys().register(Keys.FORMS_EDIT, this.pick::clickItself);

        this.column(UIConstants.MARGIN).vertical().stretch().scroll().padding(UIConstants.SCROLL_PADDING);
        this.scroll.cancelScrolling();
    }

    public void setPart(BodyPart part, Form form)
    {
        this.part = part;
        this.owner = form;

        this.removeAll();

        this.useTarget.setValue(part.useTarget.get());
        this.bone.setLabel(this.boneLabel(part.bone.get()));
        this.attachBone.setLabel(this.attachBoneLabel(part.attachBone.get()));
        this.weightEnabled.setValue(part.weightEnabled.get());
        this.weight.setValue(part.weight.get());
        this.updateWeightVisibility();

        if (!FormUtilsClient.getBones(form).isEmpty())
        {
            this.add(this.pick, this.bone);
        }
        else
        {
            this.add(this.pick);
        }

        /* The part's own bones: which of them is put onto the anchor. */
        if (part.getForm() != null && !FormUtilsClient.getBones(part.getForm()).isEmpty())
        {
            this.add(this.attachBone);
        }

        this.add(this.weightEnabled, this.weightRow, this.useTarget, this.transform);

        this.transform.setTransform(part.transform.get());

        this.scroll.setScroll(0);
        this.resize();
    }

    private IKey boneLabel(String bone)
    {
        return bone == null || bone.isEmpty() ? UIKeys.MODEL_EDITOR_PICK_BONE : IKey.constant(bone);
    }

    private IKey attachBoneLabel(String bone)
    {
        return bone == null || bone.isEmpty() ? UIKeys.FORMS_EDITOR_ATTACH_BONE_ORIGIN : IKey.constant(bone);
    }

    private void updateWeightVisibility()
    {
        this.weightRow.setVisible(this.part != null && this.part.weightEnabled.get());
        this.resize();
    }

    /**
     * A first guess at what the part weighs. Model-like forms start from a human
     * ballpark, flat and decorative ones from almost nothing, and the part's own
     * scale cubes into it - a model scaled to three times its size is roughly
     * twenty-seven times the mass. It is only a starting value: the field stays
     * editable precisely because no guess fits every model.
     */
    private static float estimateWeight(BodyPart part)
    {
        Form form = part.getForm();

        if (form == null)
        {
            return BodyPart.DEFAULT_WEIGHT;
        }

        String id = form.getFormId();
        float base;

        if (id.contains("model") || id.contains("mob"))
        {
            base = BodyPart.DEFAULT_WEIGHT;
        }
        else if (id.contains("block") || id.contains("structure"))
        {
            base = 20F;
        }
        else if (id.contains("item"))
        {
            base = 2F;
        }
        else
        {
            base = 1F;
        }

        Vector3f scale = part.transform.get().scale;
        float average = Math.abs(scale.x + scale.y + scale.z) / 3F;
        float estimate = base * average * average * average;

        return Math.round(Math.max(0.1F, Math.min(estimate, BodyPart.MAX_WEIGHT)) * 10F) / 10F;
    }

    /** Attach the active body part to the clicked parent bone; returns whether it did. */
    public boolean pickBone(Pair<Form, String> pair)
    {
        /* Ctrl + clicking to pick the parent bone to attach to */
        if (this.part != null && !pair.b.isEmpty() && this.part.getManager().getOwner() == pair.a)
        {
            this.part.bone.set(pair.b);
            this.bone.setLabel(this.boneLabel(pair.b));

            return true;
        }

        return false;
    }
}
