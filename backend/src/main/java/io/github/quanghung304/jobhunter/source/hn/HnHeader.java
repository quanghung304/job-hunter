package io.github.quanghung304.jobhunter.source.hn;

import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import java.util.List;

/**
 * What {@link HnHeaderParser} reads from the first line of a "Who is hiring?" comment.
 *
 * @param companyName    first segment, URLs and remote/onsite remarks removed; may be blank
 * @param title          first later segment naming a role, else {@link HnHeaderParser#NO_TITLE}
 * @param locationText   remote/onsite and unclassified segments joined by " | "; nullable
 * @param employmentHint employment segments ("Full-time", "Contract") joined by ", "; nullable
 * @param remoteHint     from the whole line
 * @param warnings       format problems (no separator); not run warnings, see the source spec
 */
public record HnHeader(
        String companyName,
        String title,
        String locationText,
        String employmentHint,
        RemoteHint remoteHint,
        List<String> warnings) {

    public HnHeader {
        warnings = List.copyOf(warnings);
    }
}
