package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Material tab of the model form editor. The list on the left holds the model
 * itself and every model body part added to it; the sections on the right edit
 * the selected one: its colors, how it lights and draws, and the LabPBR sliders
 * that feed shader packs.
 */
public class UIMaterialFormPanel extends UIFormPanel<ModelForm>
{
    public UIStringList parts;

    public UIColor color;
    public UIColor colorOverlay;
    public UISliderTrackpad lighting;
    public UICirculate layer;
    public UIToggle shaderShadow;
    public UISliderTrackpad smoothness;
    public UISliderTrackpad metalic;
    public UISliderTrackpad sss;
    public UISliderTrackpad pixelEmission;
    public UISliderTrackpad relief;

    /** The forms the list's entries edit, aligned with the list's indices */
    private final List<ModelForm> targets = new ArrayList<>();

    public UIMaterialFormPanel(UIForm editor)
    {
        super(editor);

        this.parts = new UIStringList((l) -> this.fillFields());
        this.parts.background();
        this.parts.relative(this).xy(10, 10).w(160).h(1F, -20);

        this.color = new UIColor((c) -> this.target((form) -> form.color.set(Color.rgba(c)))).withAlpha();
        this.color.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_COLOR_TOOLTIP);
        this.colorOverlay = new UIColor((c) -> this.target((form) -> form.colorOverlay.set(Color.rgba(c)))).withAlpha();
        this.colorOverlay.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_COLOR_OVERLAY_TOOLTIP);
        this.lighting = this.createSlider((form, v) -> form.lighting.set(v));
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_LIGHTING_TOOLTIP);

        this.layer = new UICirculate((b) -> this.target((form) -> form.renderLayer.set(b.getValue())));
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_AUTO);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_TRANSLUCENT);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_SOLID);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_CUTOUT);
        this.layer.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_TOOLTIP);
        this.shaderShadow = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_SHADER_SHADOW, (b) -> this.target((form) -> form.shaderShadow.set(b.getValue())));

        this.smoothness = this.createSlider((form, v) -> form.smoothness.set(v));
        this.metalic = this.createSlider((form, v) -> form.metalic.set(v));
        this.sss = this.createSlider((form, v) -> form.sss.set(v));
        this.pixelEmission = this.createSlider((form, v) -> form.pixelEmission.set(v));
        this.relief = this.createSlider((form, v) -> form.relief.set(v));

        UISection colorSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_COLOR, "material_color", true);

        colorSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_COLOR, this.color),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_COLOR_OVERLAY, this.colorOverlay),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_LIGHTING, this.lighting)
        );

        UISection renderingSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_RENDERING, "material_rendering", true);

        renderingSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_LAYER, this.layer),
            this.shaderShadow
        );

        UISection shadersSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_SHADERS, "material_shaders", true);

        shadersSection.title.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_SHADERS_TOOLTIP);
        shadersSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_GLOSS, this.smoothness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_METALLIC, this.metalic),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SCATTERING, this.sss),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_EMISSION, this.pixelEmission),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_RELIEF, this.relief)
        );

        this.options.add(colorSection, renderingSection, shadersSection);
        this.add(this.parts);
    }

    /** A 0..1 slider that moves in hundredths and writes into the selected form. */
    private UISliderTrackpad createSlider(BiConsumer<ModelForm, Float> setter)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) -> this.target((form) -> setter.accept(form, v.floatValue())));

        slider.limit(0D, 1D);
        slider.snap(0.01D);

        return slider;
    }

    private ModelForm getTarget()
    {
        return CollectionUtils.getSafe(this.targets, this.parts.getIndex());
    }

    private void target(Consumer<ModelForm> consumer)
    {
        ModelForm target = this.getTarget();

        if (target != null)
        {
            consumer.accept(target);
        }
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        this.targets.clear();
        this.parts.clear();
        this.targets.add(form);
        this.parts.add(UIKeys.FORMS_EDITORS_MATERIAL_MODEL.get());
        this.collectParts(form, "");
        this.parts.setIndex(0);
        this.fillFields();
    }

    /**
     * Walk the form's body parts (and theirs, all the way down) and list every
     * model form among them - the only kind these material properties live on.
     */
    private void collectParts(Form form, String prefix)
    {
        List<BodyPart> all = form.parts.getAllTyped();

        for (int i = 0; i < all.size(); i++)
        {
            BodyPart part = all.get(i);
            Form partForm = part.getForm();
            String label = prefix + (i + 1);
            String bone = part.bone.get();

            if (!bone.isEmpty())
            {
                label += " (" + bone + ")";
            }

            if (partForm instanceof ModelForm modelForm)
            {
                this.targets.add(modelForm);
                this.parts.add(label + ": " + modelForm.getDisplayName());
            }

            if (partForm != null)
            {
                this.collectParts(partForm, label + "/");
            }
        }
    }

    private void fillFields()
    {
        ModelForm target = this.getTarget();

        if (target == null)
        {
            return;
        }

        this.color.setColor(target.color.get().getARGBColor());
        this.colorOverlay.setColor(target.colorOverlay.get().getARGBColor());
        this.lighting.setValue(target.lighting.get());
        this.layer.setValue(target.renderLayer.get());
        this.shaderShadow.setValue(target.shaderShadow.get());
        this.smoothness.setValue(target.smoothness.get());
        this.metalic.setValue(target.metalic.get());
        this.sss.setValue(target.sss.get());
        this.pixelEmission.setValue(target.pixelEmission.get());
        this.relief.setValue(target.relief.get());
    }
}
