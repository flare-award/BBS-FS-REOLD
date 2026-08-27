package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.FilterClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UILabelListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Editor of {@link FilterClip}: a dope sheet of the film filter channels the clip
 * animates. Channels are added and removed through the sheet's context menu, and
 * each one keyframes the slider of the same name from the film filters overlay.
 */
public class UIFilterClip extends UIClip<FilterClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UIFilterClip(FilterClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    /** The filter slider's label for a channel id, so both UIs read the same. */
    public static IKey getChannelLabel(String id)
    {
        switch (id)
        {
            case "brightness": return UIKeys.FILM_FILTERS_BRIGHTNESS;
            case "contrast": return UIKeys.FILM_FILTERS_CONTRAST;
            case "saturation": return UIKeys.FILM_FILTERS_SATURATION;
            case "hue": return UIKeys.FILM_FILTERS_HUE;
            case "temperature": return UIKeys.FILM_FILTERS_TEMPERATURE;
            case "gamma": return UIKeys.FILM_FILTERS_GAMMA;
            case "sharpness": return UIKeys.FILM_FILTERS_SHARPNESS;
            case "vignette": return UIKeys.FILM_FILTERS_VIGNETTE;
            case "sepia": return UIKeys.FILM_FILTERS_SEPIA;
            case "grain": return UIKeys.FILM_FILTERS_GRAIN;
            case "aberration": return UIKeys.FILM_FILTERS_ABERRATION;
            case "invert": return UIKeys.FILM_FILTERS_INVERT;
            case "posterize": return UIKeys.FILM_FILTERS_POSTERIZE;
            case "pixelate": return UIKeys.FILM_FILTERS_PIXELATE;
            case "distortion": return UIKeys.FILM_FILTERS_DISTORTION;
            case "bloom": return UIKeys.FILM_FILTERS_BLOOM;
        }

        return IKey.constant(id);
    }

    /** Offer the filter channels the clip doesn't animate yet. */
    public static void offerFilterKeys(UIContext context, List<String> existing, Consumer<String> callback)
    {
        List<Label<String>> list = new ArrayList<>();

        for (String id : FilterClip.CHANNEL_IDS)
        {
            if (!existing.contains(id))
            {
                list.add(new Label<>(getChannelLabel(id), id));
            }
        }

        UILabelListOverlayPanel panel = new UILabelListOverlayPanel(UIKeys.FILM_FILTERS_CLIP_PICK, list, callback);

        UIOverlay.addOverlay(context, panel, 0.9F, 0.5F);
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
        this.keyframes.setUndoId("filter_keyframes");

        this.keyframes.view.context((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.FILM_FILTERS_CLIP_ADD, () ->
            {
                List<String> existing = new ArrayList<>();

                for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
                {
                    existing.add(channel.getId());
                }

                offerFilterKeys(this.getContext(), existing, (s) ->
                {
                    this.clip.channels.addChannel(s, KeyframeFactories.DOUBLE);
                    this.fillData();
                });
            }).order(-3);

            UIKeyframeSheet sheet = this.keyframes.view.getDopeSheet().getSheet(this.getContext().mouseY);

            if (sheet != null)
            {
                menu.action(Icons.REMOVE, UIKeys.FILM_FILTERS_CLIP_REMOVE, Colors.RED, () ->
                {
                    this.clip.channels.removeChannel(sheet.channel);
                    this.fillData();
                });
            }
        });

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

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:filters"), this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.keyframes.view.removeAllSheets();

        for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
        {
            this.addKeyframeSheet(channel);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("filters"))
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
            data.putString("embed", "filters");
        }

        super.collectUndoData(data);
    }
}
