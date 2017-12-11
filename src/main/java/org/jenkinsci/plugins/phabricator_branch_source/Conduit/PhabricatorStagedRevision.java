package org.jenkinsci.plugins.phabricator_branch_source.Conduit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PhabricatorStagedRevision extends PhabricatorRevision {
    private String repositoryUrl;
    private String branchName;
    private String baseBranchName;
    private int revisionId;

    public PhabricatorStagedRevision(String repositoryUrl, String branchName, String hash, String baseBranchName, @Nullable
    Integer revisionId, long epoch) {
        super(revisionId == null ? branchName : "D" + revisionId, hash, epoch);
        this.repositoryUrl = repositoryUrl;
        this.branchName = branchName;
        this.baseBranchName = baseBranchName;
        this.revisionId = revisionId;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getBaseBranchName() {
        return baseBranchName;
    }

    public void setBaseBranchName(String baseBranchName) {
        this.baseBranchName = baseBranchName;
    }

    public int getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(int revisionId) {
        this.revisionId = revisionId;
    }
}
