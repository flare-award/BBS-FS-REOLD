package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The one shared banner state of every landing screen: the banners that cycle in the top half
 * of the card, how each of them is framed, and the captions under the artwork.
 *
 * <p>One instance, one file: whatever the pencil in films, particles, the model editor or the
 * audio editor changes is what the next empty tab of the next panel shows. The stock set is
 * written down on the first save, so "reset to default" and "what ships in the jar" stay apart.</p>
 */
public class BannerConfig
{
    /** One banner: a file the textures manager can open (a picture or a GIF) and its crop. */
    public static class Banner
    {
        public Link link;

        /** Which slice of the photo shows, 0..1 each, (0.5, 0.5) centred. */
        public float focusX = 0.5F;
        public float focusY = 0.5F;

        /** How far in the photo is zoomed, 1 = the plain cover crop. */
        public float zoom = 1F;

        public Banner()
        {}

        public Banner(Link link)
        {
            this.link = link;
        }

        public Banner copy()
        {
            Banner banner = new Banner(this.link);

            banner.focusX = this.focusX;
            banner.focusY = this.focusY;
            banner.zoom = this.zoom;

            return banner;
        }
    }

    /* Which part of the credit line is written in bright ink; the rest stays in the muted tone. */
    public static final int CREDIT_STYLE_LAST = 0;
    public static final int CREDIT_STYLE_FIRST = 1;
    public static final int CREDIT_STYLE_ALL = 2;
    public static final int CREDIT_STYLE_NONE = 3;

    /** The credit line is a caption, not a text field: it must fit the banner. */
    public static final int MAX_CREDIT_LENGTH = 24;

    private static BannerConfig instance;

    /** The banners in the order they cycle; never empty, the last one may not be removed. */
    public final List<Banner> banners = new ArrayList<>();

    public boolean creditEnabled = true;
    public String creditText = "render by Kizrum";
    public int creditStyle = CREDIT_STYLE_LAST;

    /** The "BBS FS REOLD <version>" plate under the artwork; on or off, it keeps its own wording. */
    public boolean plateEnabled = true;

    public static BannerConfig get()
    {
        if (instance == null)
        {
            instance = load();
        }

        return instance;
    }

    /** The set that ships in the jar; what a fresh install sees before it has saved anything. */
    public static BannerConfig defaults()
    {
        BannerConfig config = new BannerConfig();

        config.banners.add(new Banner(Link.assets("textures/banners/bg1.png")));
        config.banners.add(new Banner(Link.assets("textures/banners/bg2.png")));
        config.banners.add(new Banner(Link.assets("textures/banners/bg3.png")));

        return config;
    }

    private static BannerConfig load()
    {
        File file = getFile();
        BannerConfig config = defaults();

        if (!file.isFile())
        {
            return config;
        }

        try
        {
            BaseType data = DataToString.read(file);

            if (data == null || !data.isMap())
            {
                return config;
            }

            MapType map = data.asMap();

            ListType list = map.getList("banners");

            if (list != null)
            {
                config.banners.clear();

                for (BaseType entry : list)
                {
                    if (!entry.isMap())
                    {
                        continue;
                    }

                    Banner item = readBanner(entry.asMap());

                    if (item != null)
                    {
                        config.banners.add(item);
                    }
                }
            }

            /* A broken file may have lost every banner; the cycle needs at least one. */
            if (config.banners.isEmpty())
            {
                return defaults();
            }

            config.creditEnabled = map.getBool("creditEnabled", true);
            config.creditText = map.getString("creditText", "render by Kizrum");
            config.creditStyle = Math.max(0, Math.min(map.getInt("creditStyle", CREDIT_STYLE_LAST), CREDIT_STYLE_NONE));
            config.plateEnabled = map.getBool("plateEnabled", true);

            return config;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return config;
        }
    }

    public static File getFile()
    {
        return BBSMod.getSettingsPath("banners.json");
    }

    private static Banner readBanner(MapType map)
    {
        String link = map.getString("link", "");

        if (link.isEmpty())
        {
            return null;
        }

        Banner banner = new Banner(Link.create(link));
        banner.focusX = Math.max(0F, Math.min(map.getFloat("focusX", 0.5F), 1F));
        banner.focusY = Math.max(0F, Math.min(map.getFloat("focusY", 0.5F), 1F));
        banner.zoom = Math.max(map.getFloat("zoom", 1F), 1F);

        return banner;
    }

    private static MapType bannerData(Banner banner)
    {
        MapType entry = new MapType();

        entry.put("link", new StringType(banner.link.toString()));
        entry.put("focusX", new FloatType(banner.focusX));
        entry.put("focusY", new FloatType(banner.focusY));
        entry.put("zoom", new FloatType(banner.zoom));

        return entry;
    }

    /** Write the shared state down; every change in the pencil editor ends here. */
    public static void save()
    {
        BannerConfig config = get();

        try
        {
            DataToString.write(getFile(), config.toData());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /* Presets */

    /** The whole shared state as one preset. */
    public MapType toData()
    {
        MapType map = new MapType();

        ListType list = new ListType();

        for (Banner banner : this.banners)
        {
            list.add(bannerData(banner));
        }

        map.put("banners", list);
        map.put("creditEnabled", new IntType(this.creditEnabled ? 1 : 0));
        map.put("creditText", new StringType(this.creditText));
        map.put("creditStyle", new IntType(this.creditStyle));
        map.put("plateEnabled", new IntType(this.plateEnabled ? 1 : 0));

        return map;
    }

    /** Take a preset's state over the shared one and keep it on disk. */
    public static void fromData(MapType map)
    {
        BannerConfig config = get();

        config.banners.clear();

        ListType list = map.getList("banners");

        if (list != null)
        {
            for (BaseType entry : list)
            {
                if (!entry.isMap())
                {
                    continue;
                }

                Banner banner = readBanner(entry.asMap());

                if (banner != null)
                {
                    config.banners.add(banner);
                }
            }
        }

        if (config.banners.isEmpty())
        {
            /* A preset without banners would leave the landing screens bare; keep what we had. */
            return;
        }

        config.creditEnabled = map.getBool("creditEnabled", true);
        config.creditText = map.getString("creditText", "render by Kizrum");
        config.creditStyle = Math.max(0, Math.min(map.getInt("creditStyle", CREDIT_STYLE_LAST), CREDIT_STYLE_NONE));
        config.plateEnabled = map.getBool("plateEnabled", true);

        save();
    }
}
