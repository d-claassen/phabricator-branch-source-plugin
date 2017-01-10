package org.jenkinsci.plugins.phabricator_branch_source;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.views.ViewJobFilter;
import jenkins.scm.api.SCMHead;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by claassen on 10-01-17.
 */
public class BranchJobFilter extends AbstractBranchJobFilter {
    @DataBoundConstructor
    public BranchJobFilter() {}

    @Override
    protected boolean shouldShow(SCMHead head) {
        return head instanceof DifferentialSCMHead && ((DifferentialSCMHead) head).getRevisionId() == null;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ViewJobFilter> {
        @Override
        public String getDisplayName() {
            return "Phabricator Branch Jobs Only";
        }
    }
}
