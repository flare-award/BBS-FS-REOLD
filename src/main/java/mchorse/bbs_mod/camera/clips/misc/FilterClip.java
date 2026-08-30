package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValueChannels;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera clip that animates the film's post-processing filters with keyframes.
 *
 * <p>Every channel is named after a film filter setting and holds that setting's
 * values over the clip's span. While the clip plays, the interpolated values land
 * in the context's clip data, where the client's film effects pick them up and
 * override the corresponding sliders for that frame.</p>
 */
public class FilterClip extends CameraClip
{
    /** Channel ids the clip can animate, in the order the sliders are shown in. */
    public static final List<String> CHANNEL_IDS = Arrays.asList(
        "brightness", "contrast", "saturation", "hue",
        "temperature", "gamma", "sharpness", "vignette",
        "sepia", "grain", "aberration", "invert",
        "posterize", "pixelate", "distortion", "bloom",
        "radial", "vhs", "flip", "fisheye"
    );

    public final ValueChannels channels = new ValueChannels("channels");

    public static Map<String, Double> getValues(ClipContext context)
    {
        return context.clipData.get("filter_data", HashMap::new);
    }

    public FilterClip()
    {
        this.add(this.channels);

        /* Every filter channel is present from the start, so the whole set shows
         * up in the keyframe editor right away; empty channels change nothing. */
        for (String id : CHANNEL_IDS)
        {
            this.channels.addChannel(id);
        }
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        /* Clips saved before the full set existed re-gain the missing channels */
        for (String id : CHANNEL_IDS)
        {
            if (!(this.channels.get(id) instanceof KeyframeChannel))
            {
                this.channels.addChannel(id);
            }
        }
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        Map<String, Double> values = getValues(context);

        for (KeyframeChannel<Double> channel : this.channels.getChannels())
        {
            if (!channel.isEmpty())
            {
                values.put(channel.getId(), channel.interpolate(context.relativeTick + context.transition));
            }
        }
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        for (KeyframeChannel<?> channel : this.channels.getAllKeyframeChannels())
        {
            breakDownTrimAfterSplit(channel, offset);
        }

        FilterClip filterClip = (FilterClip) original;

        for (KeyframeChannel<?> channel : filterClip.channels.getAllKeyframeChannels())
        {
            breakDownTrimOriginalTail(channel, offset);
        }
    }

    /** Shift keyframes after splitting: drop everything before tick 0 on the new clip. */
    private static void breakDownTrimAfterSplit(KeyframeChannel<?> channel, int offset)
    {
        channel.moveX(-offset);

        KeyframeSegment<?> segment = channel.find(0);

        if (segment != null)
        {
            while (segment.a != channel.get(0))
            {
                channel.remove(0);
            }
        }
    }

    /** On the source clip, drop keyframes after the split point. */
    private static void breakDownTrimOriginalTail(KeyframeChannel<?> channel, int offset)
    {
        KeyframeSegment<?> segment = channel.find(offset);

        if (segment == null)
        {
            return;
        }

        while (segment.b != channel.get(channel.getKeyframes().size() - 1))
        {
            channel.remove(channel.getKeyframes().size() - 1);
        }
    }

    @Override
    protected Clip create()
    {
        return new FilterClip();
    }
}
