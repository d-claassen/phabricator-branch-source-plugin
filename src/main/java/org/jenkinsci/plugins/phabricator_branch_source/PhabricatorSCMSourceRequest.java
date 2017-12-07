package org.jenkinsci.plugins.phabricator_branch_source;

import hudson.Util;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.PhabricatorBranch;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PhabricatorSCMSourceRequest extends SCMSourceRequest {
    /**
     * {@code true} if branch details need to be fetched.
     */
    private final boolean fetchBranches;
    /**
     * {@code true} if revision details need to be fetched.
     */
    private final boolean fetchRevisions;
    /**
     * The set of revision numbers that the request is scoped to or {@code null} if the request is not limited.
     */
    @CheckForNull
    private final Set<String> requestedRevisionNumbers;
    /**
     * The set of branch names that the request is scoped to or {@code null} if the request is not limited.
     */
    @CheckForNull
    private final Set<String> requestedBranchNames;
    /**
     * The {@link PhabricatorSCMSource#getRepository()}.
     */
    @Nonnull
    private final String repository;
    /**
     * The pull request details or {@code null} if not {@link #isFetchRevisions()}.
     * @TODO Iterable<PhabricatorRevision>?
     */
    @CheckForNull
    private Iterable<DifferentialSCMHead> revisions;
    /**
     * The branch details or {@code null} if not {@link #isFetchBranches()}.
     */
    @CheckForNull
    private Iterable<PhabricatorBranch> branches;

    /**
     * Constructor.
     *
     * @param source The source.
     * @param context The context.
     * @param listener The listener.
     */
    protected PhabricatorSCMSourceRequest(@Nonnull final PhabricatorSCMSource source,
                                          @Nonnull PhabricatorSCMSourceContext context,
                                          @CheckForNull TaskListener listener) {
        super(source, context, listener);
        fetchBranches = context.wantBranches();
        fetchRevisions = context.wantRevisions();

        Set<SCMHead> includes = context.observer().getIncludes();
        if(includes != null) {
            Set<String> branchNames = new HashSet<>(includes.size());
            Set<String> revisionNumbers = new HashSet<>(includes.size());
            for (SCMHead h : includes) {
                if (h instanceof BranchSCMHead) {
                    branchNames.add(h.getName());
                } else if (h instanceof DifferentialSCMHead) {
                    revisionNumbers.add(((DifferentialSCMHead) h).getRevisionId().toString());
                    branchNames.add(h.getName());
                }
            }
            this.requestedRevisionNumbers = Collections.unmodifiableSet(revisionNumbers);
            this.requestedBranchNames = Collections.unmodifiableSet(branchNames);
        } else {
            requestedRevisionNumbers = null;
            requestedBranchNames = null;
        }
        // @TODO
        // repoOwner = source.getRepoOwner();
        repository = source.getRepository();
    }

    /**
     * Returns {@code true} if branch details need to be fetched.
     *
     * @return {@code true} if branch details need to be fetched.
     */
    public final boolean isFetchBranches() {
        return fetchBranches;
    }

    /**
     * Returns {@code true} if tag details need to be fetched.
     *
     * @return {@code true} if tag details need to be fetched.
     */
    public final boolean isFetchRevisions() {
        return fetchRevisions;
    }

    /**
     * Returns requested revision numbers.
     *
     * @return the requested revision numbers or {@code null} if the request was not scoped to a subset of revisions.
     */
    @CheckForNull
    public final Set<String> getRequestedRevisionNumbers() {
        return requestedRevisionNumbers;
    }

    /**
     * Gets requested branch names.
     *
     * @return the requested branch names or {@code null} if the request was not scoped to a subset of branches.
     */
    @CheckForNull
    public final Set<String> getRequestedBranchNames() {
        return requestedBranchNames;
    }

    /**
     * Returns the {@link PhabricatorSCMSource#getRepository()}.
     *
     * @return the {@link PhabricatorSCMSource#getRepository()}.
     */
    @Nonnull
    public final String getRepository() {
        return repository;
    }

    /**
     * Provides the requests with the revision details.
     *
     * @param revisions the revision details.
     */
    public final void setRevisions(@CheckForNull Iterable<DifferentialSCMHead> revisions) {
        this.revisions = revisions;
    }

    /**
     * Returns the revision details or an empty list if either the request did not specify to {@link #isFetchRevisions()}
     * or if the revision details have not been provided by {@link #setRevisions(Iterable)} yet.
     *
     * @return the revision details (may be empty)
     */
    @Nonnull
    public final Iterable<DifferentialSCMHead> getRevisions() {
        return Util.fixNull(revisions);
    }

    /**
     * Provides the requests with the branch details.
     *
     * @param branches the branch details.
     */
    public final void setBranches(@CheckForNull Iterable<PhabricatorBranch> branches) {
        this.branches = branches;
    }

    /**
     * Returns the branch details or an empty list if either the request did not specify to {@link #isFetchBranches()}
     * or if the branch details have not been provided by {@link #setBranches(Iterable)} yet.
     *
     * @return the branch details (may be empty)
     */
    @Nonnull
    public final Iterable<PhabricatorBranch> getBranches() {
        return Util.fixNull(branches);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (revisions instanceof Closeable) {
            ((Closeable) revisions).close();
        }
        if (branches instanceof Closeable) {
            ((Closeable) branches).close();
        }
        super.close();
    }

}
