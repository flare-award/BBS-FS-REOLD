package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.BiConsumer;

/**
 * Material tab of the form editor. It always edits the form currently picked
 * in the editor's form list on the left - pick a body part (or a model nested
 * through body parts) there, and this tab edits that one: its colors, how it
 * lights and draws, and the LabPBR sliders that feed shader packs.
 */
public class UIMaterialFormPanel extends UIFormPanel
{
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

    public UIMaterialFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.setColor(Color.rgba(c))).withAlpha();
        this.color.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_COLOR_TOOLTIP);
        this.colorOverlay = new UIColor((c) -> this.form.colorOverlay.set(Color.rgba(c))).withAlpha();
        this.colorOverlay.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_COLOR_OVERLAY_TOOLTIP);
        this.lighting = this.createSlider((form, v) -> form.lighting.set(v));
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_LIGHTING_TOOLTIP);

        this.layer = new UICirculate((b) -> this.form.renderLayer.set(b.getValue()));
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_AUTO);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_TRANSLUCENT);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_SOLID);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_CUTOUT);
        this.layer.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_TOOLTIP);
        this.shaderShadow = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_SHADER_SHADOW, (b) -> this.form.shaderShadow.set(b.getValue()));

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
    }

    /** A 0..1 slider that moves in hundredths and writes into the edited form. */
    private UISliderTrackpad createSlider(BiConsumer<Form, Float> setter)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) -> setter.accept(this.form, v.floatValue()));

        slider.limit(0D, 1D);
        slider.snap(0.01D);

        return slider;
    }

    private void setColor(Color value)
    {
        ValueColor colorValue = this.getColorValue();

        if (colorValue != null)
        {
            colorValue.set(value);
        }
    }

    /**
     * The form's tint color, when its kind has one (model, billboard and most
     * others do). It isn't part of the base form, hence the lookup by id.
     */
    private ValueColor getColorValue()
    {
        if (this.form != null && this.form.get("color") instanceof ValueColor value)
        {
            return value;
        }

        return null;
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        ValueColor colorValue = this.getColorValue();

        this.color.setEnabled(colorValue != null);
        this.color.setColor(colorValue == null ? Colors.WHITE : colorValue.get().getARGBColor());
        this.colorOverlay.setColor(form.colorOverlay.get().getARGBColor());
        this.lighting.setValue(form.lighting.get());
        this.layer.setValue(form.renderLayer.get());
        this.shaderShadow.setValue(form.shaderShadow.get());
        this.smoothness.setValue(form.smoothness.get());
        this.metalic.setValue(form.metalic.get());
        this.sss.setValue(form.sss.get());
        this.pixelEmission.setValue(form.pixelEmission.get());
        this.relief.setValue(form.relief.get());
    }
}
