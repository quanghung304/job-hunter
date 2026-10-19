package io.github.quanghung304.jobhunter.tracking;

import java.util.List;

/** One page of the board; {@code page} is 0-based. */
public record JobBoardPage(List<JobBoardRow> items, int page, int size, long totalItems) {

    public JobBoardPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }
}
