package io.github.quanghung304.jobhunter.source;

/** A company job board an ATS collector reads ({@code company} row). */
public record CompanyBoard(long id, String name, AtsType ats, String slug) {

    /** "GitLab (GREENHOUSE gitlab)", for warnings and logs. */
    public String label() {
        return name + " (" + ats + " " + slug + ")";
    }
}
