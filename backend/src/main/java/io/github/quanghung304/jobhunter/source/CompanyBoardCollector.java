package io.github.quanghung304.jobhunter.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Base of the ATS collectors (docs/sources/ats.md): reads the board of every enabled company of
 * its ATS types, one request per company with a pause between them, and records each board's
 * check status in {@link CompanyBoards}. {@code since} is ignored (a board lists all open jobs).
 * A 404 or failing board is a warning (PARTIAL); the run fails only if no board could be read
 * and at least one failed with an error other than 404.
 */
public abstract class CompanyBoardCollector implements JobCollector {

    private final CompanyBoards boards;
    private final Politeness politeness;
    private final Duration requestDelay;

    protected CompanyBoardCollector(CompanyBoards boards, Politeness politeness, Duration requestDelay) {
        this.boards = boards;
        this.politeness = politeness;
        this.requestDelay = requestDelay;
    }

    /** ATS types whose boards this collector reads. */
    public abstract Set<AtsType> atsTypes();

    /**
     * Fetches the board's jobs as raw JSON (one node per job).
     *
     * @throws RestClientException for HTTP errors; a 404 means the board does not exist
     */
    protected abstract List<JsonNode> fetchBoard(CompanyBoard board);

    /**
     * Maps one job; empty for a job that is skipped on purpose (e.g. unlisted).
     *
     * @throws JsonProcessingException  if the job cannot be read
     * @throws IllegalArgumentException if a required field is missing or unreadable
     */
    protected abstract Optional<NormalizedPosting> map(CompanyBoard board, JsonNode raw) throws JsonProcessingException;

    @Override
    public CollectResult collect(CollectRequest request) {
        var postings = new ArrayList<NormalizedPosting>();
        var warnings = new ArrayList<String>();
        int fetched = 0;
        int boardsRead = 0;
        String firstError = null;
        List<CompanyBoard> enabled = boards.enabled(atsTypes());
        for (int i = 0; i < enabled.size(); i++) {
            CompanyBoard board = enabled.get(i);
            try {
                if (i > 0) {
                    politeness.pause(requestDelay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CollectResult.failed(fetched, "interrupted before " + board.label());
            }
            List<JsonNode> jobs;
            try {
                jobs = fetchBoard(board);
            } catch (HttpClientErrorException.NotFound e) {
                int count = boards.recordNotFound(board.id());
                warnings.add(board.label() + ": board not found (404, " + count + " in a row"
                        + (count >= CompanyBoards.MAX_NOT_FOUND ? ", disabled)" : ")"));
                continue;
            } catch (RestClientException e) {
                boards.recordError(board.id());
                String message = board.label() + ": " + HttpErrors.describe(e);
                warnings.add(message);
                firstError = firstError == null ? message : firstError;
                continue;
            }
            boards.recordOk(board.id());
            boardsRead++;
            for (JsonNode raw : jobs) {
                fetched++;
                mapItem(board, raw, warnings).ifPresent(postings::add);
                if (postings.size() >= request.maxItems()) {
                    return CollectResult.completed(postings, fetched, warnings);
                }
            }
        }
        if (boardsRead == 0 && firstError != null) {
            return CollectResult.failed(fetched, firstError);
        }
        return CollectResult.completed(postings, fetched, warnings);
    }

    /**
     * Fetches one board once and records its status (command line {@code --validate-companies}).
     * Unlike {@link #collect}, this also checks disabled boards; an OK board is enabled again.
     */
    public BoardCheck check(CompanyBoard board) {
        try {
            int jobs = fetchBoard(board).size();
            boards.recordOk(board.id());
            return new BoardCheck(board, CompanyCheckStatus.OK, jobs, null);
        } catch (HttpClientErrorException.NotFound e) {
            boards.recordNotFound(board.id());
            return new BoardCheck(board, CompanyCheckStatus.NOT_FOUND, 0, HttpErrors.describe(e));
        } catch (RestClientException e) {
            boards.recordError(board.id());
            return new BoardCheck(board, CompanyCheckStatus.ERROR, 0, HttpErrors.describe(e));
        }
    }

    private Optional<NormalizedPosting> mapItem(CompanyBoard board, JsonNode raw, List<String> warnings) {
        try {
            return map(board, raw);
        } catch (JsonProcessingException e) {
            warnings.add(itemLabel(board, raw) + ": unreadable item: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            warnings.add(itemLabel(board, raw) + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private static String itemLabel(CompanyBoard board, JsonNode raw) {
        JsonNode id = raw == null ? null : raw.get("id");
        return board.label() + " item " + (id != null && !id.isNull() ? id.asText() : "(no id)");
    }

    /**
     * Result of {@link #check}.
     *
     * @param jobs    jobs on the board (all of them, before mapping); 0 unless OK
     * @param message the HTTP error; null when OK
     */
    public record BoardCheck(CompanyBoard board, CompanyCheckStatus status, int jobs, String message) {
    }
}
