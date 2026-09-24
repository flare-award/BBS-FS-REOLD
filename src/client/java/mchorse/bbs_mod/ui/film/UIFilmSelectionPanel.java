package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UISelectionScreen;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.Direction;

import java.util.List;

public class UIFilmSelectionPanel extends UISelectionScreen<Film>
{
    private UIIcon duplicateCurrentFilm;

    public UIFilmSelectionPanel(UIFilmPanel panel)
    {
        super(panel);

        UIIcon editBanner = new UIIcon(Icons.EDIT, (b) -> this.openBannerEditor());

        editBanner.tooltip(UIKeys.FILM_BANNER_TOOLTIP, Direction.BOTTOM);
        editBanner.wh(20, 20);
        editBanner.relative(this.banner).x(1F, -24).y(4);
        this.banner.add(editBanner);
    }

    private void openBannerEditor()
    {
        UIOverlay.addOverlay(this.getContext(), new UIFilmBannerOverlayPanel(), UIFilmBannerOverlayPanel.WIDTH, UIFilmBannerOverlayPanel.HEIGHT);
    }

    @Override
    protected Link getBannerTexture()
    {
        Link custom = UIFilmBannerOverlayPanel.getCustomBanner();

        return custom == null ? super.getBannerTexture() : custom;
    }

    @Override
    protected float getBannerFocusX()
    {
        return BBSSettings.filmsBannerX.get();
    }

    @Override
    protected float getBannerFocusY()
    {
        return BBSSettings.filmsBannerY.get();
    }

    @Override
    protected float getBannerZoom()
    {
        return BBSSettings.filmsBannerZoom.get();
    }

    @Override
    protected IKey getHeaderTitle()
    {
        return UIKeys.FILM_TITLE;
    }

    @Override
    protected Icon getFileIcon()
    {
        return Icons.FILM;
    }

    @Override
    protected void appendHeaderIcons(List<UIIcon> icons)
    {
        this.duplicateCurrentFilm = new UIIcon(Icons.SCENE, (b) -> this.openDuplicateCurrentFilmPrompt());
        this.duplicateCurrentFilm.wh(20, 20);

        icons.add(this.duplicateCurrentFilm);
    }

    @Override
    protected void updateCustomActionButtons(List<DataPath> selected)
    {
        if (this.duplicateCurrentFilm != null)
        {
            UIFilmPanel filmPanel = (UIFilmPanel) this.panel;
            boolean hasSelectedFilm = false;

            for (DataPath dataPath : selected)
            {
                if (this.isFilmFile(dataPath))
                {
                    hasSelectedFilm = true;

                    break;
                }
            }

            this.duplicateCurrentFilm.setEnabled(filmPanel.getData() != null || hasSelectedFilm);
        }
    }

    private void openDuplicateCurrentFilmPrompt()
    {
        UIFilmPanel filmPanel = (UIFilmPanel) this.panel;
        DataPath current = this.namesList.getCurrentFirst();
        String sourceId = this.getSourceFilmId(filmPanel, current);

        if (sourceId == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_DUPE,
            UIKeys.PANELS_MODALS_DUPE,
            (str) -> filmPanel.dupeFilmTo(sourceId, this.namesList.getPath(str).toString())
        );

        if (this.isFilmFile(current))
        {
            panel.text.setText(current.getLast());
        }
        else if (filmPanel.getData().getId() != null)
        {
            panel.text.setText(new DataPath(filmPanel.getData().getId()).getLast());
        }

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private boolean isFilmFile(DataPath path)
    {
        return path != null && !path.folder && !"..".equals(path.getLast());
    }

    private String getSourceFilmId(UIFilmPanel filmPanel, DataPath selected)
    {
        if (this.isFilmFile(selected))
        {
            return selected.toString();
        }

        Film data = filmPanel.getData();

        return data == null ? null : data.getId();
    }

    @Override
    protected void onDuplicateData(Film data)
    {
        if (data != null)
        {
            data.stampCreationTimeNow();
        }
    }
}
