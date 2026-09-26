package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * The space around the form in the editor's viewport: what the background is - the world,
 * a solid color, a photo, or the studio grid. Everything here is stored on the form itself,
 * so each form keeps its own space and a new one starts in the normal one.
 */
public class UISpaceFormPanel extends UIFormPanel
{
    private UICirculate mode;
    private UIColor color;
    private UIButton pickPhoto;
    private UIIcon resetPhoto;
    private UITrackpad photoOpacity;
    private UITrackpad photoSize;
    private UITrackpad photoX;
    private UITrackpad photoY;
    private UITrackpad photoRotate;

    private UISection modeSection;
    private UISection colorSection;
    private UISection photoSection;

    public UISpaceFormPanel(UIForm editor)
    {
        super(editor);

        this.mode = new UICirculate((b) ->
        {
            if (this.form != null)
            {
                this.form.spaceMode.set(b.getValue());
                this.refreshByMode();
            }
        });
        this.mode.addLabel(UIKeys.FORMS_EDITORS_SPACE_MODE_NORMAL);
        this.mode.addLabel(UIKeys.FORMS_EDITORS_SPACE_MODE_SOLID);
        this.mode.addLabel(UIKeys.FORMS_EDITORS_SPACE_MODE_PHOTO);
        this.mode.addLabel(UIKeys.FORMS_EDITORS_SPACE_MODE_STUDIO);
        this.mode.tooltip(UIKeys.FORMS_EDITORS_SPACE_MODE_TOOLTIP);
        UIValues.resettable(this.mode, () -> this.form == null ? null : this.form.spaceMode, () ->
        {
            if (this.form != null)
            {
                this.mode.setValue(MathUtils.clamp(this.form.spaceMode.get(), 0, 3));
            }
        });

        this.color = UIValues.color(() -> this.form == null ? null : this.form.spaceColor);
        this.color.tooltip(UIKeys.FORMS_EDITORS_SPACE_COLOR_TOOLTIP);

        this.pickPhoto = new UIButton(UIKeys.FORMS_EDITORS_SPACE_PHOTO_PICK, (b) -> this.pickPhoto());
        this.pickPhoto.tooltip(UIKeys.FORMS_EDITORS_SPACE_PHOTO_PICK_TOOLTIP);

        this.resetPhoto = new UIIcon(Icons.REFRESH, (b) ->
        {
            if (this.form != null)
            {
                this.form.spacePhoto.set("");
                this.refreshByMode();
                UIUtils.playClick();
            }
        });
        this.resetPhoto.tooltip(UIKeys.FORMS_EDITORS_SPACE_PHOTO_RESET);

        this.photoOpacity = UIValues.trackpad(() -> this.form == null ? null : this.form.spacePhotoOpacity);
        this.photoOpacity.limit(0D, 1D);
        this.photoOpacity.tooltip(UIKeys.FORMS_EDITORS_SPACE_PHOTO_OPACITY_TOOLTIP);

        this.photoSize = UIValues.trackpad(() -> this.form == null ? null : this.form.spacePhotoScale);
        this.photoSize.limit(BBSSettings.MIN_FILM_PHOTO_SCALE, BBSSettings.MAX_FILM_PHOTO_SCALE);
        this.photoSize.tooltip(UIKeys.FORMS_EDITORS_SPACE_PHOTO_SIZE_TOOLTIP);

        this.photoX = UIValues.trackpad(() -> this.form == null ? null : this.form.spacePhotoX);
        this.photoX.limit(-BBSSettings.MAX_FILM_PHOTO_OFFSET, BBSSettings.MAX_FILM_PHOTO_OFFSET);

        this.photoY = UIValues.trackpad(() -> this.form == null ? null : this.form.spacePhotoY);
        this.photoY.limit(-BBSSettings.MAX_FILM_PHOTO_OFFSET, BBSSettings.MAX_FILM_PHOTO_OFFSET);

        this.photoRotate = UIValues.trackpad(() -> this.form == null ? null : this.form.spacePhotoRotate);
        this.photoRotate.limit(-180D, 180D);
        this.photoRotate.tooltip(UIKeys.FORMS_EDITORS_SPACE_PHOTO_ROTATE_TOOLTIP);

        this.modeSection = this.section(UIKeys.FORMS_EDITORS_SPACE_SECTION_MODE, "space.mode", true);
        this.modeSection.fields.add(UI.labelRow(UIKeys.FORMS_EDITORS_SPACE_MODE, this.mode));

        this.colorSection = this.section(UIKeys.FORMS_EDITORS_SPACE_SECTION_COLOR, "space.color", true);
        this.colorSection.fields.add(UI.labelRow(UIKeys.FORMS_EDITORS_SPACE_COLOR, this.color));

        this.photoSection = this.section(UIKeys.FORMS_EDITORS_SPACE_SECTION_PHOTO, "space.photo", true);
        this.photoSection.fields.add(
            UI.row(this.pickPhoto, this.resetPhoto),
            UI.labelRow(UIKeys.FORMS_EDITORS_SPACE_PHOTO_OPACITY, this.photoOpacity),
            UI.labelRow(UIKeys.FORMS_EDITORS_SPACE_PHOTO_SIZE, this.photoSize),
            UI.labelRow(UIKeys.GENERAL_X, this.photoX),
            UI.labelRow(UIKeys.GENERAL_Y, this.photoY),
            UI.labelRow(UIKeys.FORMS_EDITORS_SPACE_PHOTO_ROTATE, this.photoRotate)
        );

        this.options.add(this.modeSection, this.colorSection, this.photoSection);
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        this.refreshByMode();
    }

    /**
     * Show only the controls of the current mode, and only the photo ones the photo can
     * answer for. The sections remember their fold state across form switches.
     */
    private void refreshByMode()
    {
        int mode = this.form == null ? Form.SPACE_NORMAL : MathUtils.clamp(this.form.spaceMode.get(), 0, 3);
        boolean hasPhoto = this.form != null && !this.form.spacePhoto.get().isEmpty();

        this.colorSection.setVisible(mode == Form.SPACE_SOLID);
        this.photoSection.setVisible(mode == Form.SPACE_PHOTO);
        this.pickPhoto.setVisible(mode == Form.SPACE_PHOTO);
        this.resetPhoto.setVisible(mode == Form.SPACE_PHOTO && hasPhoto);
        this.photoOpacity.setVisible(mode == Form.SPACE_PHOTO && hasPhoto);
        this.photoSize.setVisible(mode == Form.SPACE_PHOTO && hasPhoto);
        this.photoX.setVisible(mode == Form.SPACE_PHOTO && hasPhoto);
        this.photoY.setVisible(mode == Form.SPACE_PHOTO && hasPhoto);
        this.photoRotate.setVisible(mode == Form.SPACE_PHOTO && hasPhoto);

        this.options.resize();
    }

    private void pickPhoto()
    {
        if (this.form == null)
        {
            return;
        }

        Link current = this.form.spacePhoto.get().isEmpty() ? null : Link.create(this.form.spacePhoto.get());
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        UITexturePicker.open(context, current, (picked) ->
        {
            if (this.form != null)
            {
                this.form.spacePhoto.set(picked == null ? "" : picked.toString());
                this.refreshByMode();
            }
        });
    }
}
