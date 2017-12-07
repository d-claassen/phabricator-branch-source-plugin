package org.jenkinsci.plugins.phabricator_branch_source;

import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * The {@link SCMSourceContext} for Phabricator.
 * We need a context because we are using traits.
 */
public class PhabricatorSCMSourceContext extends SCMSourceContext<PhabricatorSCMSourceContext, PhabricatorSCMSourceRequest> {
    /**
     * {@code true} if the {@link PhabricatorSCMSourceRequest} will need information about branches.
     */
    private boolean wantBranches;
    /**
     * {@code true} if the {@link PhabricatorSCMSourceRequest} will need information about Differential revisions.
     */
    private boolean wantRevisions;

    /**
     * Constructor.
     *
     * @param criteria Optional criteria.
     * @param observer The {@link SCMHeadObserver}.
     */
    public PhabricatorSCMSourceContext(@CheckForNull SCMSourceCriteria criteria,
                                       @Nonnull SCMHeadObserver observer) {
        super(criteria, observer);
    }

    /**
     * Returns {@code true} if the {@link PhabricatorSCMSourceRequest} will need information about branches.
     *
     * @return {@code true} if the {@link PhabricatorSCMSourceRequest} will need information about branches.
     */
    public final boolean wantBranches() {
        return wantBranches;
    }

    /**
     * Returns {@code true} if the {@link PhabricatorSCMSourceRequest} will need information about revisions.
     *
     * @return {@code true} if the {@link PhabricatorSCMSourceRequest} will need information about revisions.
     */
    public final boolean wantRevisions() {
        return wantRevisions;
    }

    /**
     * Adds a requirement for branch details to any {@link PhabricatorSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @Nonnull
    public final PhabricatorSCMSourceContext wantBranches(boolean include) {
        wantBranches = wantBranches || include;
        return this;
    }

    /**
     * Adds a requirement for revision details to any {@link PhabricatorSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @Nonnull
    public final PhabricatorSCMSourceContext wantRevisions(boolean include) {
        wantRevisions = wantRevisions || include;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PhabricatorSCMSourceRequest newRequest(@Nonnull SCMSource scmSource, TaskListener taskListener) {
        return new PhabricatorSCMSourceRequest((PhabricatorSCMSource) scmSource, this, taskListener);
    }
}
