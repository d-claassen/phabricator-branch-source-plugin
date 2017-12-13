package org.jenkinsci.plugins.phabricator_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

/**
 * Created by claassen on 10-01-17.
 */
public class DifferentialSCMHead extends SCMHead { //implements ChangeRequestSCMHead {

    private String repoUrl;
    private String branchName;
    private Integer revisionId;
    private String baseBranchRemoteName;
    private String baseBranchTargetName;
    private BranchSCMHead target;

    /**
     * Constructor.
     *
     * @param name the name.
     */
    public DifferentialSCMHead(String repoUrl, String name, String branchName, String baseBranchName, Integer revisionId, BranchSCMHead target) {
        super(name);
        this.repoUrl = repoUrl;
        this.branchName = branchName;
        this.revisionId = revisionId;
        this.target = target;

        String[] splitName = baseBranchName.split("/", 2);
        baseBranchRemoteName = splitName[0];
        baseBranchTargetName = splitName[1];
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getTagName() {
        return getBranchName();
    }

    public String getBaseBranchRemoteName() {
        return baseBranchRemoteName;
    }

    public String getBaseBranchTargetName() {
        return baseBranchTargetName;
    }

    public Integer getRevisionId() {
        return revisionId;
    }

    public String getBranchName() {
        return branchName;
    }

    @NonNull
//    @Override
    public String getId() {
        return revisionId.toString();
    }

    @NonNull
//    @Override
    public SCMHead getTarget() {
        return target;
    }
}
