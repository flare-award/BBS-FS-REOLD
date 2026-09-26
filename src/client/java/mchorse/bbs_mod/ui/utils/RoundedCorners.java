package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * The "corner rounding" setting, read where the interface draws. The radii are per corner,
 * so a corner pressed against a screen edge stays square while the others round - a
 * Windows-11-style rounding.
 */
public class RoundedCorners
{
    /** The base radius, in the same scaled UI pixels the interface is drawn in. */
    public static final float RADIUS = 10F;

    /** Whether the setting rounds at all: 0 - off, 1 - smooth, 2 - pixel. */
    public static int style()
    {
        return BBSSettings.roundCorners == null ? 0 : BBSSettings.roundCorners.get();
    }

    public static boolean enabled()
    {
        return style() > 0;
    }

    /**
     * The radii of the four corners (top left, top right, bottom right, bottom left) of
     * {@code area} - the base radius, except where a corner touches a screen edge. The
     * screen is the context's menu, which is the whole window.
     */
    public static float[] radii(Area area, UIContext context)
    {
        if (!enabled())
        {
            return new float[] {0F, 0F, 0F, 0F};
        }

        int width = context.menu.width;
        int height = context.menu.height;

        boolean top = area.y <= 1;
        boolean bottom = area.ey() >= height - 1;
        boolean left = area.x <= 1;
        boolean right = area.ex() >= width - 1;

        float r = RADIUS;

        return new float[]
        {
            (left || top) ? 0F : r,
            (right || top) ? 0F : r,
            (right || bottom) ? 0F : r,
            (left || bottom) ? 0F : r
        };
    }
}
