package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.PhotoClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Editor of {@link PhotoClip}: a dope sheet of the animated photo layer's
 * channels. The texture channel keyframes which photo shows (numbered files
 * from one folder step through in order, billboard-style), and the rest
 * keyframe the layer's placement from the photo overlay.
 */
public class UIPhotoClip extends UIClip<PhotoClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UIPhotoClip(PhotoClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    /** The photo overlay's label for a channel id, so both UIs read the same. */
    public static IKey getChannelLabel(String id)
    {
        switch (id)
        {
            case "texture": return UIKeys.FILM_PHOTO_PICK;
            case "opacity": return UIKeys.FILM_PHOTO_OPACITY;
            case "x": return UIKeys.GENERAL_X;
            case "y": return UIKeys.GENERAL_Y;
            case "scale": return UIKeys.FILM_PHOTO_SCALE;
            case "stretch_x": return UIKeys.FILM_PHOTO_STRETCH_X;
            case "stretch_y": return UIKeys.FILM_PHOTO_STRETCH_Y;
            case "rotate": return UIKeys.FILM_PHOTO_ROTATE;
        }

        return IKey.constant(id);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("photo_keyframes");

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private void addKeyframeSheet(KeyframeChannel<?> channel)
    {
        int sheetColor = channel.getId().hashCode() & Colors.RGB;

        this.keyframes.view.addSheet(new UIKeyframeSheet(channel.getId(), getChannelLabel(channel.getId()), sheetColor, false, channel, null));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:photo"), this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.keyframes.view.removeAllSheets();
        this.addKeyframeSheet(this.clip.texture);
        this.addKeyframeSheet(this.clip.opacity);
        this.addKeyframeSheet(this.clip.x);
        this.addKeyframeSheet(this.clip.y);
        this.addKeyframeSheet(this.clip.scale);
        this.addKeyframeSheet(this.clip.stretchX);
        this.addKeyframeSheet(this.clip.stretchY);
        this.addKeyframeSheet(this.clip.rotate);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("photo"))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }

        super.applyUndoData(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        if (this.keyframes.hasParent())
        {
            data.putString("embed", "photo");
        }

        super.collectUndoData(data);
    }
}
