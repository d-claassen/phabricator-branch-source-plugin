package org.jenkinsci.plugins.phabricator_branch_source;

import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.views.ViewJobFilter;
import jenkins.scm.api.SCMHead;

import java.util.List;

abstract class AbstractBranchJobFilter extends ViewJobFilter {
    public AbstractBranchJobFilter() {}

    @Override
    public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        for (TopLevelItem item : all) {
            if (added.contains(item))  continue;   // already in there

            SCMHead head = SCMHead.HeadByItem.findHead(item);
            if (head != null && shouldShow(head)) {
                added.add(item);
            }

        }
        return added;
    }

    protected abstract boolean shouldShow(SCMHead head);
}
