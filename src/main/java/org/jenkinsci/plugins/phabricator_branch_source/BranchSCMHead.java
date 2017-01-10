package org.jenkinsci.plugins.phabricator_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;

/**
 * Head corresponding to a branch.
 * @since FIXME
 */
public class BranchSCMHead extends SCMHead {
    private String repoUrl;

    /**
     * {@inheritDoc}
     */
    public BranchSCMHead(@NonNull String name, @NonNull String repoUrl) {
        super(name);
        this.repoUrl = repoUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return "Branch";
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
}