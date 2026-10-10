package _959.server_waypoint.common.client.gui.screens;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs the preview's families of samples into rows from the samples' widths alone, so the screen can
 * pack again when the preview's width changes. The families keep their order and stay on one row when
 * they fit; a family wider than a row wraps its samples onto as many rows as it needs, so nothing is
 * cut off.
 */
final class PreviewPacking {
    private PreviewPacking() {
    }

    /**
     * Where a sample goes: the index of its family in the list given to {@link #pack}, its index in the
     * family, and the distance from its row's start to its left edge.
     */
    record Placement(int family, int sample, int x) {
    }

    /**
     * Puts the families into rows, in order. A family's width is its samples' widths and a sample gap
     * between each two of them. For each family:
     * <ul>
     *     <li>It stays whole on the current row, a family gap after the row's last sample, when it fits
     *     there, or at the row's start when the row is empty and it fits.</li>
     *     <li>Otherwise, if it fits a row, it starts a new row and stays whole.</li>
     *     <li>Otherwise it is wider than a row. It starts a new row, unless the current one is empty,
     *     and goes sample by sample, a sample gap apart, with a new row for each sample that would pass
     *     the row's width while the row isn't empty.</li>
     * </ul>
     * A row is never empty, and a sample wider than a row gets a row of its own.
     *
     * @param familyWidths the widths of each family's samples, in the order they are shown
     * @param rowWidth the width of a row
     * @param sampleGap the gap between the samples of a family
     * @param familyGap the gap between families that share a row
     * @return the rows of placements, with {@code x} counted from the row's start
     */
    static List<List<Placement>> pack(List<List<Integer>> familyWidths, int rowWidth, int sampleGap, int familyGap) {
        List<List<Placement>> rows = new ArrayList<>();
        List<Placement> row = new ArrayList<>();
        int end = 0; // where the row's last sample ends
        for (int family = 0; family < familyWidths.size(); family++) {
            List<Integer> widths = familyWidths.get(family);
            int familyWidth = familyWidth(widths, sampleGap);
            for (int sample = 0; sample < widths.size(); sample++) {
                int width = widths.get(sample);
                // The first sample asks for room for its whole family, so a family that fits a row stays
                // whole; the others ask only for themselves, so a family wider than a row wraps by sample.
                int needed = sample == 0 ? familyWidth : width;
                int x = row.isEmpty() ? 0 : end + (sample == 0 ? familyGap : sampleGap);
                if (!row.isEmpty() && x + needed > rowWidth) {
                    rows.add(row);
                    row = new ArrayList<>();
                    x = 0;
                }
                row.add(new Placement(family, sample, x));
                end = x + width;
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    /** A family's width on one row: its samples and the sample gaps between them. */
    private static int familyWidth(List<Integer> sampleWidths, int sampleGap) {
        int width = sampleGap * Math.max(0, sampleWidths.size() - 1);
        for (int sampleWidth : sampleWidths) {
            width += sampleWidth;
        }
        return width;
    }
}
