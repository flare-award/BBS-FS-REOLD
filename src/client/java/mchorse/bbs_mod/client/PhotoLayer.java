package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * One photo layer of the film's overlay. A film can stack any number of these,
 * each with its own texture, placement and rotation. The whole
 * stack serializes into a single string setting, so it travels together with
 * the rest of the film filter values (and their presets).
 */
public class PhotoLayer
{
    public String texture = "";
    public float opacity = 1F;
    public float x;
    public float y;
    public float scale = 1F;
    public float stretchX = 1F;
    public float stretchY = 1F;

    /** Clockwise rotation in degrees */
    public float rotate;

    public static List<PhotoLayer> parseList(String serialized)
    {
        List<PhotoLayer> layers = new ArrayList<>();

        if (serialized == null || serialized.isEmpty())
        {
            return layers;
        }

        ListType list = DataToString.listFromString(serialized);

        if (list == null)
        {
            return layers;
        }

        for (BaseType type : list)
        {
            if (type.isMap())
            {
                PhotoLayer layer = new PhotoLayer();

                layer.fromData(type.asMap());
                layers.add(layer);
            }
        }

        return layers;
    }

    public static String serializeList(List<PhotoLayer> layers)
    {
        if (layers.isEmpty())
        {
            return "";
        }

        ListType list = new ListType();

        for (PhotoLayer layer : layers)
        {
            list.add(layer.toData());
        }

        return DataToString.toString(list);
    }

    public PhotoLayer copy()
    {
        PhotoLayer layer = new PhotoLayer();

        layer.fromData(this.toData());

        return layer;
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("texture", this.texture);
        data.putFloat("opacity", this.opacity);
        data.putFloat("x", this.x);
        data.putFloat("y", this.y);
        data.putFloat("scale", this.scale);
        data.putFloat("stretch_x", this.stretchX);
        data.putFloat("stretch_y", this.stretchY);
        data.putFloat("rotate", this.rotate);

        return data;
    }

    public void fromData(MapType data)
    {
        this.texture = data.getString("texture", "");
        this.opacity = MathUtils.clamp(data.getFloat("opacity", 1F), 0F, 1F);
        this.x = MathUtils.clamp(data.getFloat("x", 0F), -BBSSettings.MAX_FILM_PHOTO_OFFSET, BBSSettings.MAX_FILM_PHOTO_OFFSET);
        this.y = MathUtils.clamp(data.getFloat("y", 0F), -BBSSettings.MAX_FILM_PHOTO_OFFSET, BBSSettings.MAX_FILM_PHOTO_OFFSET);
        this.scale = MathUtils.clamp(data.getFloat("scale", 1F), BBSSettings.MIN_FILM_PHOTO_SCALE, BBSSettings.MAX_FILM_PHOTO_SCALE);
        this.stretchX = MathUtils.clamp(data.getFloat("stretch_x", 1F), BBSSettings.MIN_FILM_PHOTO_STRETCH, BBSSettings.MAX_FILM_PHOTO_STRETCH);
        this.stretchY = MathUtils.clamp(data.getFloat("stretch_y", 1F), BBSSettings.MIN_FILM_PHOTO_STRETCH, BBSSettings.MAX_FILM_PHOTO_STRETCH);
        this.rotate = MathUtils.clamp(data.getFloat("rotate", 0F), -180F, 180F);
    }
}
