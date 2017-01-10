package org.jenkinsci.plugins.phabricator_branch_source;

import java.util.List;


import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.views.ViewJobFilter;
import jenkins.scm.api.SCMHead;

abstract class AbstractBranchJobFilter extends ViewJobFilter {
    public AbstractBranchJobFilter() {}

    @Override
    public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        for (TopLevelItem item : all) {
            if (added.contains(item))  continue;   // already in there

            SCMHead head = SCMHead.HeadByItem.findHead(item);
            if (head instanceof DifferentialSCMHead && shouldShow(head)) {
                added.add(item);
            }

        }
        return added;
    }

    protected abstract boolean shouldShow(SCMHead head);
}
