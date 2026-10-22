package io.github.quanghung304.jobhunter.source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Fetches every company board once, enabled or not, and records its status (docs/runbook.md,
 * "Validate company boards"). Used by {@code --validate-companies}; never runs on its own.
 */
@Component
public class CompanyBoardChecker {

    static final Duration DELAY = Duration.ofSeconds(1);

    private final List<CompanyBoardCollector> collectors;
    private final CompanyBoards boards;
    private final Politeness politeness;

    public CompanyBoardChecker(List<CompanyBoardCollector> collectors, CompanyBoards boards, Politeness politeness) {
        this.collectors = collectors;
        this.boards = boards;
        this.politeness = politeness;
    }

    /** @throws InterruptedException if cancelled during a pause */
    public List<CompanyBoardCollector.BoardCheck> checkAll() throws InterruptedException {
        var results = new ArrayList<CompanyBoardCollector.BoardCheck>();
        for (CompanyBoard board : boards.all(EnumSet.allOf(AtsType.class))) {
            CompanyBoardCollector collector = collectors.stream()
                    .filter(c -> c.atsTypes().contains(board.ats()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No collector for " + board.ats()));
            if (!results.isEmpty()) {
                politeness.pause(DELAY);
            }
            results.add(collector.check(board));
        }
        return results;
    }

    /** One line per board: status, job count, ATS, slug, name (and the error). */
    public static String report(List<CompanyBoardCollector.BoardCheck> checks) {
        var out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%-9s %5s  %-10s %-24s %s%n", "STATUS", "JOBS", "ATS", "SLUG", "NAME"));
        for (CompanyBoardCollector.BoardCheck check : checks) {
            out.append(String.format(Locale.ROOT, "%-9s %5d  %-10s %-24s %s%s%n", check.status(), check.jobs(),
                    check.board().ats(), check.board().slug(), check.board().name(),
                    check.message() == null ? "" : "  (" + check.message() + ")"));
        }
        long ok = checks.stream().filter(c -> c.status() == CompanyCheckStatus.OK).count();
        out.append(String.format(Locale.ROOT, "%d of %d boards OK%n", ok, checks.size()));
        return out.toString();
    }
}
