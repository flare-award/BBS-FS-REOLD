package mchorse.bbs_mod.film;

import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The film's playhead, published per actor.
 *
 * <p>Simulated forms (the web's rope, for instance) need to know <em>when</em> in
 * the film they are being drawn. The entity's own age cannot answer that: while a
 * film is paused the actor is barely ticked at all, and dragging the playhead
 * across the whole film moves the age by a tick or two - which is exactly what
 * "scrubbing only nudges the web forward" looked like. The controller knows the
 * real answer (the replay-local tick it just applied), so it leaves it here and
 * renderers pick it up.</p>
 *
 * <p>Entries are weak (an actor that goes away takes its entry with it) and they
 * expire shortly after the film stops updating, so a form rendered outside of any
 * film falls back to normal world time instead of a stale playhead.</p>
 */
public class FilmActorClock
{
    /**
     * How long (in milliseconds) an entry stays trustworthy. A running film
     * refreshes it every client tick (50 ms), so anything older than this means
     * the film is no longer driving this entity.
     */
    private static final long LIFETIME = 500L;

    private static final Map<IEntity, Entry> TICKS = new WeakHashMap<>();

    public static void set(IEntity entity, int tick)
    {
        if (entity == null)
        {
            return;
        }

        synchronized (TICKS)
        {
            Entry entry = TICKS.get(entity);

            if (entry == null)
            {
                TICKS.put(entity, new Entry(tick));
            }
            else
            {
                entry.tick = tick;
                entry.time = System.currentTimeMillis();
            }
        }
    }

    /**
     * @return the film tick this entity was last updated to, or {@code null} when
     *         no film is currently driving it.
     */
    public static Integer get(IEntity entity)
    {
        if (entity == null)
        {
            return null;
        }

        synchronized (TICKS)
        {
            Entry entry = TICKS.get(entity);

            if (entry == null)
            {
                return null;
            }

            if (System.currentTimeMillis() - entry.time > LIFETIME)
            {
                TICKS.remove(entity);

                return null;
            }

            return entry.tick;
        }
    }

    public static void clear()
    {
        synchronized (TICKS)
        {
            TICKS.clear();
        }
    }

    private static class Entry
    {
        public int tick;
        public long time;

        public Entry(int tick)
        {
            this.tick = tick;
            this.time = System.currentTimeMillis();
        }
    }
}
