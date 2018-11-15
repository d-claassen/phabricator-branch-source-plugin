package org.jenkinsci.plugins.phabricator_branch_source;

import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;
import org.eclipse.jgit.annotations.NonNull;

public class RevisionSCMRevision<R extends SCMRevision> extends ChangeRequestSCMRevision<DifferentialSCMHead> {

    /**
     * Constructor.
     *
     * @param head   the head.
     * @param target the target revision.
     */
    public RevisionSCMRevision(@NonNull DifferentialSCMHead head, @NonNull R target) {
        super(head, target);
    }

    @Override
    public boolean equivalent(ChangeRequestSCMRevision<?> o) {
        if (!(o instanceof RevisionSCMRevision)) {
            return false;
        }
        RevisionSCMRevision other = (RevisionSCMRevision) o;
        return getHead().equals(other.getHead());
    }

    @Override
    protected int _hashCode() {
        return getHead().hashCode();
    }
}
