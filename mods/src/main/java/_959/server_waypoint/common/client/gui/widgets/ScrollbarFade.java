package _959.server_waypoint.common.client.gui.widgets;

/** Frame-driven scrollbar opacity using monotonic nanosecond timestamps. */
public final class ScrollbarFade {
    private static final long FADE_IN_NANOS = 150_000_000L;
    private static final long HIDE_DELAY_NANOS = 500_000_000L;
    private static final long FADE_OUT_NANOS = 300_000_000L;
    private boolean showing;
    private long transitionTime;
    private float transitionOpacity;

    public float update(boolean shouldShow, long now) {
        float opacity = opacityAt(now);
        if (shouldShow != this.showing) {
            this.showing = shouldShow;
            this.transitionTime = now;
            this.transitionOpacity = opacity;
        }
        return opacity;
    }

    /** Disabled or non-overflowing panels disappear immediately. */
    public void reset() {
        this.showing = false;
        this.transitionOpacity = 0;
        this.transitionTime = 0;
    }

    private float opacityAt(long now) {
        long elapsed = now - this.transitionTime;
        if (this.showing) {
            float progress = Math.max(0, Math.min(1, (float) elapsed / FADE_IN_NANOS));
            return this.transitionOpacity + (1 - this.transitionOpacity) * progress;
        }
        float progress = Math.max(0, Math.min(1, (float) (elapsed - HIDE_DELAY_NANOS) / FADE_OUT_NANOS));
        return this.transitionOpacity * (1 - progress);
    }
}
