package org.jenkinsci.plugins.phabricator_branch_source;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.uber.jenkins.phabricator.ConduitCredentialsDescriptor;
import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.ChangelogToBranch;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.*;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.Diffusion;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.DiffusionClient;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.PhabricatorBranch;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.PhabricatorRevision;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * SCM source implementation for Phabricator.
 *
 * It provides a way to discover and retrieve branches and Differential Revisions through the Phabricator API.
 */
public class PhabricatorSCMSource extends SCMSource {

    /**
     * Url of the Phabricator instance
     */
    private String phabricatorServerUrl;

    /**
     * Credentials used to access the Phabricator API.
     */
    private String phabCredentialsId;

    /**
     * Repository PHID
     */
    private String repository;

    /**
     * Credentials used to access the repository.
     */
    private String repoCredentialsId;

    private static final Logger LOGGER = Logger.getLogger(PhabricatorSCMSource.class.getName());

    /**
     * Using traits is not required but it does make your implementation easier for others to extend.
     */
    @NonNull
    private List<SCMSourceTrait> traits;

    @DataBoundConstructor
    public PhabricatorSCMSource(String id, String repository) {
        super(id);
        this.repository = repository;
    }

    @CheckForNull
    public String getPhabCredentialsId() {
        return phabCredentialsId;
    }

    @DataBoundSetter
    public void setPhabCredentialsId(String phabCredentialsId) {
        this.phabCredentialsId = Util.fixEmpty(phabCredentialsId);
    }

    public String getRepository() {
        return repository;
    }

    public String getRepoCredentialsId() {
        return repoCredentialsId;
    }

    @DataBoundSetter
    public void setRepoCredentialsId(String repoCredentialsId) {
        this.repoCredentialsId = Util.fixEmpty(repoCredentialsId);
    }

    @DataBoundSetter
    public void setPhabricatorServerUrl(String url) {
        this.phabricatorServerUrl = Util.fixEmpty(url);
        if (this.phabricatorServerUrl != null) {
            // Remove a possible trailing slash
            this.phabricatorServerUrl = this.phabricatorServerUrl.replaceAll("/$", "");
        }
    }

    @CheckForNull
    public String getPhabricatorServerUrl() {
        return phabricatorServerUrl;
    }

    @NonNull
    public List<SCMSourceTrait> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    @DataBoundSetter
    public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    public ConduitCredentials credentials() {
        return ConduitCredentialsDescriptor.getCredentials(null, phabCredentialsId);
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria,
                            @NonNull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        listener.getLogger().format("Start retrieving now%n");
        try(PhabricatorSCMSourceRequest request = new PhabricatorSCMSourceContext(criteria, observer)
                                .withTraits(traits)
                                .newRequest(this, listener)) {

            listener.getLogger().format("Connecting to %s with credentials%n", credentials().getUrl());

            // populate the request with its data sources
            if(request.isFetchRevisions()) {
                request.setRevisions(new LazyIterable<PhabricatorRevision>() {
                    @Override
                    protected Iterable<PhabricatorRevision> create() {
                        try {
                            DiffusionClient diffusionClient = new DiffusionClient(buildClient());
                            return diffusionClient.getRevisions(repository);
                        } catch(IOException | ConduitAPIException e) {
                            throw new PhabricatorSCMSource.WrappedException(e);
                        }
                    }
                });
            }
            if(request.isFetchBranches()) {
                request.setBranches(new LazyIterable<PhabricatorBranch>() {
                    @Override
                    protected Iterable<PhabricatorBranch> create() {
                        try {
                            DiffusionClient diffusionClient = new DiffusionClient(buildClient());
                            return diffusionClient.getBranches(repository);
                        } catch (ConduitAPIException | IOException e) {
                            throw new PhabricatorSCMSource.WrappedException(e);
                        }
                    }
                });
            }

            // now server the request
            if(request.isFetchBranches() && !request.isComplete()) {
                retrieveBranches(request);
            }
            if(request.isFetchRevisions() && !request.isComplete()) {
                retrieveDifferentialRevisions(request);
            }
        } catch (WrappedException e) {
            e.unwrap();
        }


//        ConduitAPIClient client = buildClient();
//        retrieveBranches(client, observer, listener);
//
//        retrieveDifferentialRevisions(client, observer, listener);
    }

    private ConduitAPIClient buildClient() {
        ConduitCredentials credentials = credentials();
        return new ConduitAPIClient(credentials.getUrl(), credentials.getToken().getPlainText());
    }

    private void retrieveBranches(final PhabricatorSCMSourceRequest request) throws IOException, InterruptedException {
        String fullName = repository;
        request.listener().getLogger().println("Looking up " + fullName + " for branches");

        String url;
        final ConduitAPIClient client = buildClient();
        try {
            DiffusionClient diffusionClient = new DiffusionClient(client);
            Diffusion diffusion = diffusionClient.getRepository(repository);
            url = diffusion.getPrimaryUrl();
        }
        catch( ConduitAPIException e )
        {
            return;
        }

        int count = 0;
        for (final PhabricatorBranch branch : request.getBranches()) {
            request.listener().getLogger().println("Checking branch " + branch.getName() + " from " + fullName);
            count++;

            SCMHead head = new BranchSCMHead(branch.getName(), url);
            if(request.process(head,
                    new SCMSourceRequest.IntermediateLambda<String>() {
                        @Nullable
                        @Override
                        public String create() {
                            return branch.getHash();
                        }
                    },
                    new PhabricatorProbeFactory(client, request),
                    new PhabricatorRevisionFactory(),
                    new CriteriaWitness(request)
            )) {
                request.listener().getLogger().format("%n  %d branches were processed (query completed)%n", count);
                break;
            }
        }
        request.listener().getLogger().format("%n  %d branches were processed%n", count);
    }

    private void retrieveBranches(ConduitAPIClient client, @NonNull SCMHeadObserver observer, @NonNull TaskListener listener ) throws InterruptedException {
        try {
            DiffusionClient diffusionClient = new DiffusionClient(client);
            Diffusion diffusion = diffusionClient.getRepository(repository);
            String url = diffusion.getPrimaryUrl();

            listener.getLogger().format("Repo url: %s.%n", url);
            listener.getLogger().format("Looking up all open branches.%n");

            JSONObject params = new JSONObject();
            params.element("closed", false);
            params.element("repository", repository);

            JSONObject branchesResponse = client.perform("diffusion.branchquery", params);
            if(!branchesResponse.has("result")) {
                listener.getLogger().format("Could not find any branches.%n");
                return;
            }

            JSONArray openBranches = branchesResponse.getJSONArray("result");

            int nrOpenBranches = openBranches.size();
            listener.getLogger().format("Done. Found %s open branches.%n", nrOpenBranches);
            for (Integer i = 0; i < nrOpenBranches; i++) {
                String branchName = openBranches.getJSONObject(i).getString("shortName");
//                String branchRef = openBranches.getJSONObject(i).getJSONObject("rawFields").getString("refname");
                String commitHash = openBranches.getJSONObject(i).getString("commitIdentifier");

                listener.getLogger().format("Observe branch %s.%n", branchName);
                SCMHead head = new BranchSCMHead(branchName, url);

                SCMRevision revision = new AbstractGitSCMSource.SCMRevisionImpl(head, commitHash);

                observe(observer, listener, head, revision);
//                observe(observer, listener, repositoryUrl, branchName, branchRef, "", 0);
            }
        } catch( IOException | ConduitAPIException e ) {
            listener.getLogger().format("Error: %s%n", e.getMessage());
        }
        listener.getLogger().println();
    }

    private void retrieveDifferentialRevisions(final PhabricatorSCMSourceRequest request) throws IOException, InterruptedException {
        String fullName = repository;
        request.listener().getLogger().println("Looking up " + fullName + " for revisions");
    }

    private void retrieveDifferentialRevisions(ConduitAPIClient client, @NonNull SCMHeadObserver observer, @NonNull TaskListener listener ) throws InterruptedException {
        try {
            listener.getLogger().format("Looking up all open revisions%n");
            JSONObject openParams = new JSONObject();
            openParams.element("status", "status-open");
            JSONObject openResponse = client.perform("differential.query", openParams);
            JSONArray openRevisions = openResponse.getJSONArray("result");
            JSONArray openPhids = new JSONArray();
            Integer nrOfOpenRevisions = openRevisions.size();
            for (Integer i = 0; i < nrOfOpenRevisions; i++) {
                openPhids.add(openRevisions.getJSONObject(i).getString("phid"));
            }
            listener.getLogger().format("Found %d open revisions for all repositories%n", nrOfOpenRevisions);

            // should be a query to get open revisions, this is currently not possible:
            // - the builtin query "all" returns closed revisions
            // - the builtin query "active" returns revisions for which the current user is responsible (not all open)
            // - constraint status/statuses is not supported
            JSONObject params = new JSONObject();
            JSONArray repositoryPHIDs = new JSONArray();
            repositoryPHIDs.add(repository);
            JSONObject constraints = new JSONObject();
            constraints.element("repositoryPHIDs", repositoryPHIDs);
            if(openPhids.size() > 0 ) {
                constraints.element("phids", openPhids);
            }
            params.element("constraints", constraints);

            listener.getLogger().format("Looking up open revisions for repository.%n");
            // Retrieve all (should be only open) revisions for the current repository
            JSONObject response = client.perform("differential.revision.search", params);

            if(response.has("result") && response.getJSONObject("result").has("data")) {
                JSONArray revisions = response.getJSONObject("result").getJSONArray("data");

                listener.getLogger().format("Found %d open revisions for the current repository%n", revisions.size());

                for (int i = 0; i < revisions.size(); i++) {
                    JSONObject revision = revisions.getJSONObject(i);
                    JSONObject fields = revision.getJSONObject("fields");
                    listener.getLogger().format("%nChecking revision D%s: %s%n", revision.get("id").toString(), fields.get("title").toString());

                    JSONObject diffParams = new JSONObject();
                    JSONArray revisionIDs = new JSONArray();
                    revisionIDs.add(revision.get("id").toString());
                    diffParams.element("revisionIDs", revisionIDs);

                    JSONObject diffResponse = client.perform("differential.querydiffs", diffParams);
                    JSONObject diffs = diffResponse.getJSONObject("result");
                    listener.getLogger().format("Found %s diffs for revision %s%n", diffs.size(), revision.get("id").toString());
                    if(diffs.size() > 0) {
                        Iterator<?> keys = diffs.keys();
                        while (keys.hasNext()) {
                            String key = (String) keys.next();
                            listener.getLogger().format("Getting diff %s.%n", key);
                            JSONObject diff = diffs.getJSONObject(key);

                            if(!diff.has("properties") ) {
                                continue;
                            }

                            JSONObject properties = diff.optJSONObject("properties");
                            if(properties == null) {
                                continue;
                            }


                            if(!properties.has("arc.staging") || !properties.getJSONObject("arc.staging").get("status").equals("pushed")) {
                                continue;
                            }
                            listener.getLogger().format("Diff %s has changes staged%n", key);

                            JSONObject staging =  properties.getJSONObject("arc.staging");
                            JSONArray stagedData = staging.getJSONArray("refs");
                            JSONObject diffRef = null;
                            JSONObject baseRef = null;
                            for (int j = 0, c = stagedData.size(); j < c; j++) {
                                JSONObject ref = stagedData.getJSONObject(j);
                                listener.getLogger().format("Diff %s ref type %s%n", key, ref.get("type"));
                                if (ref.get("type").equals("diff")) {
                                    diffRef = ref;
                                } else if(ref.get("type").equals("base")) {
                                    baseRef = ref;
                                }
                            }

                            if(baseRef != null && diffRef != null) {
                                observe(observer, listener, diffRef.getJSONObject("remote").get("uri").toString(), diffRef.get("ref").toString(),
                                        diffRef.get("commit").toString(), baseRef.get("ref").toString(), revision.getInt("id"));

                                if (!observer.isObserving()) {
                                    return;
                                }
                            }
                        }
                    }
                    checkInterrupt();
                }
            }

        }
        catch( IOException | ConduitAPIException e ) {
            listener.getLogger().format("Exception: %s%n", e.toString());
        }
        listener.getLogger().format("%nDone examining repository%n");
    }

    private void observe(SCMHeadObserver observer, TaskListener listener, SCMHead head, SCMRevision revision) throws IOException, InterruptedException {
        listener.getLogger().format("%nStart observing now...%n");

        observer.observe(head, revision);
    }

    private void observe(SCMHeadObserver observer, TaskListener listener, String repositoryUrl, String branchName, String hash, String baseBranchName, @Nullable Integer revisionId) throws IOException, InterruptedException {
        String name = revisionId == null ? branchName : "D" + revisionId;

        listener.getLogger().format("Repo url %s%n", repositoryUrl);
        listener.getLogger().format("Head name %s%n", name);
        listener.getLogger().format("Branch %s%n", branchName);
        listener.getLogger().format("Base Branch %s%n", baseBranchName);
        listener.getLogger().format("Differential Revision id %s%n", revisionId);
        DifferentialSCMHead head = new DifferentialSCMHead(repositoryUrl, name, branchName, baseBranchName, revisionId);
        listener.getLogger().format("Hash %s%n", hash);
        SCMRevision revision = new AbstractGitSCMSource.SCMRevisionImpl(head, hash);

        observe(observer, listener, head, revision);
    }

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        // @todo if repo not git -> exception. won't handle yet.
        // Quite defensive. Should always be true.
        if(head instanceof DifferentialSCMHead) {
            DifferentialSCMHead h = (DifferentialSCMHead) head;

            BuildChooser buildChooser = revision instanceof AbstractGitSCMSource.SCMRevisionImpl ? new AbstractGitSCMSource.SpecificRevisionBuildChooser(
                    (AbstractGitSCMSource.SCMRevisionImpl) revision) : new DefaultBuildChooser();

            ArrayList<GitSCMExtension> extensions = new ArrayList<>();
            extensions.add(new BuildChooserSetting(buildChooser));
            extensions.add(new ChangelogToBranch(new ChangelogToBranchOptions(h.getBaseBranchRemoteName(), h.getBaseBranchTargetName())));
            return new GitSCM(
                    getGitRemoteConfigs(h),
                    Collections.singletonList(new BranchSpec(h.getTagName())),
                    false, Collections.<SubmoduleConfig>emptyList(),
                    null, null,
                    extensions);
        } else if(head instanceof BranchSCMHead) {
            BranchSCMHead h = (BranchSCMHead) head;

            BuildChooser buildChooser = revision instanceof AbstractGitSCMSource.SCMRevisionImpl ? new AbstractGitSCMSource.SpecificRevisionBuildChooser(
                    (AbstractGitSCMSource.SCMRevisionImpl) revision) : new DefaultBuildChooser();

            ArrayList<GitSCMExtension> extensions = new ArrayList<>();
            extensions.add(new BuildChooserSetting(buildChooser));
            return new GitSCM(
                    getGitRemoteConfigs(h),
                    Collections.singletonList(new BranchSpec("+refs/heads/*:refs/remotes/origin/*")),
                    false, Collections.<SubmoduleConfig>emptyList(),
                    null, null,
                    extensions);

        }
        throw new IllegalArgumentException("Can't handle this yet");
    }

    public List<UserRemoteConfig> getGitRemoteConfigs(SCMHead head) throws IllegalArgumentException {
        if (head instanceof DifferentialSCMHead) {
            DifferentialSCMHead h = (DifferentialSCMHead) head;

            List<UserRemoteConfig> result = new ArrayList<UserRemoteConfig>();
            result.add(new UserRemoteConfig(h.getRepoUrl(), "origin", "+refs/tags/phabricator/*:refs/remotes/origin/tags/phabricator/*", repoCredentialsId));
            return result;
        } else if(head instanceof BranchSCMHead) {
            List<UserRemoteConfig> result = new ArrayList<UserRemoteConfig>();
            BranchSCMHead h = (BranchSCMHead) head;
            String refspec = "+refs/heads/" + h.getName();
            result.add(new UserRemoteConfig(h.getRepoUrl(), "origin", refspec, repoCredentialsId));
            return result;
        }
        throw new IllegalArgumentException("Can't handle this ");
    }

    public String getRemote() {
        return "http://....git";
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

        @Override
        public String getDisplayName() {
            return "Phabricator";
        }

        public ListBoxModel doFillPhabCredentialsIdItems(@AncestorInPath SCMSourceOwner context) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            result.withMatching(
                    CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(ConduitCredentials.class)),
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, context)
                );
            return result;
        }

        public FormValidation doCheckPhabCredentialsId(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.warning("Credentials are required to retrieve possible Phabricator repositories");
            } else {
                ConduitCredentials credentials = ConduitCredentialsDescriptor.getCredentials(null, value);
                if(credentials.getUrl().isEmpty()) {
                    return FormValidation.warning("Credentials are missing url");
                }

                ConduitAPIClient client = new ConduitAPIClient(credentials.getUrl(), credentials.getToken().getPlainText());
                try {
                    client.perform("conduit.ping", new JSONObject());
                    return FormValidation.ok();
                }
                catch( IOException | ConduitAPIException e) {
                    return FormValidation.warning("Could not connect to "+credentials.getUrl());
                }
            }
        }

        public ListBoxModel doFillRepositoryItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String phabCredentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            phabCredentialsId = Util.fixEmpty(phabCredentialsId);
            if(phabCredentialsId == null) {
                return result.withEmptySelection();
            }

            ConduitCredentials credentials = ConduitCredentialsDescriptor.getCredentials(null, phabCredentialsId);
            ConduitAPIClient client = new ConduitAPIClient(credentials.getUrl(), credentials.getToken().getPlainText());
            try {
                DiffusionClient diffusionClient = new DiffusionClient(client);
                ArrayList<Diffusion> diffusions = diffusionClient.getActiveRepositories();
                for (Diffusion diff : diffusions) {
                    result.add(diff.getName(), diff.getPhid());
                }
            }
            catch( IOException | ConduitAPIException e ) {
                result.add(e.getMessage());
                result.withEmptySelection();
            }

            return result;
        }

        public ListBoxModel doFillRepoCredentialsIdItems(@AncestorInPath SCMSourceOwner context) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            result.withMatching(
                    CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardCredentials.class)),
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, context)
            );

            return result;
        }


        public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
            List<SCMSourceTraitDescriptor> all = new ArrayList<>();
            // all that are applicable to our context
            all.addAll(SCMSourceTrait._for(this, PhabricatorSCMSourceContext.class, null));
            // all that are applicable to our builders
//            all.addAll(SCMSourceTrait._for(this, null, BitbucketGitSCMBuilder.class));
//            all.addAll(SCMSourceTrait._for(this, null, BitbucketHgSCMBuilder.class));
//            Set<SCMSourceTraitDescriptor> dedup = new HashSet<>();
//            for (Iterator<SCMSourceTraitDescriptor> iterator = all.iterator(); iterator.hasNext(); ) {
//                SCMSourceTraitDescriptor d = iterator.next();
//                if (dedup.contains(d)
//                        || d instanceof MercurialBrowserSCMSourceTrait.DescriptorImpl
//                        || d instanceof GitBrowserSCMSourceTrait.DescriptorImpl) {
                    // remove any we have seen already and ban the browser configuration as it will always be bitbucket
//                    iterator.remove();
//                } else {
//                    dedup.add(d);
//                }
//            }
            List<NamedArrayList<? extends SCMSourceTraitDescriptor>> result = new ArrayList<>();
            NamedArrayList.select(all, "Within repository", NamedArrayList
                            .anyOf(NamedArrayList.withAnnotation(Discovery.class),
                                    NamedArrayList.withAnnotation(Selection.class)),
                    true, result);
            int insertionPoint = result.size();
            NamedArrayList.select(all, "Git", new NamedArrayList.Predicate<SCMSourceTraitDescriptor>() {
                @Override
                public boolean test(SCMSourceTraitDescriptor d) {
                    return GitSCM.class.isAssignableFrom(d.getScmClass());
                }
            }, true, result);
//            NamedArrayList.select(all, "Mercurial", new NamedArrayList.Predicate<SCMSourceTraitDescriptor>() {
//                @Override
//                public boolean test(SCMSourceTraitDescriptor d) {
//                    return MercurialSCM.class.isAssignableFrom(d.getScmClass());
//                }
//            }, true, result);
            NamedArrayList.select(all, "General", null, true, result, insertionPoint);
            return result;
        }
    }

    private static class WrappedException extends RuntimeException {
        public WrappedException(Throwable cause) {
            super(cause);
        }

        public void unwrap() throws IOException, InterruptedException {
            Throwable cause = getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw this;
        }
    }

    private static class CriteriaWitness implements SCMSourceRequest.Witness {
        private final PhabricatorSCMSourceRequest request;

        public CriteriaWitness(PhabricatorSCMSourceRequest request) {
            this.request = request;
        }

        @Override
        public void record(@NonNull SCMHead scmHead, SCMRevision revision, boolean isMatch) {
            if (revision == null) {
                request.listener().getLogger().println("    Skipped");
            } else {
                if (isMatch) {
                    request.listener().getLogger().println("    Met criteria");
                } else {
                    request.listener().getLogger().println("    Does not meet criteria");
                    return;
                }

            }
        }
    }

    private class PhabricatorRevisionFactory implements SCMSourceRequest.LazyRevisionLambda<SCMHead, SCMRevision, String> {
        @Nonnull
        @Override
        public SCMRevision create(@Nonnull SCMHead head, @Nullable String hash) throws IOException, InterruptedException {
            return new AbstractGitSCMSource.SCMRevisionImpl(head, hash);
        }
    }

    private static class PhabricatorProbeFactory implements SCMSourceRequest.ProbeLambda<SCMHead, String> {
        private final ConduitAPIClient client;
        private final PhabricatorSCMSourceRequest request;

        public PhabricatorProbeFactory(ConduitAPIClient client, PhabricatorSCMSourceRequest request) {
            this.client = client;
            this.request = request;
        }

        @NonNull
        @Override
        public SCMSourceCriteria.Probe create(@NonNull final SCMHead head,
                                              @Nullable String hash)
                                throws IOException, InterruptedException {
            return new SCMSourceCriteria.Probe() {
                @Override
                public String name() {
                     return head.getName();
                }

                @Override
                public long lastModified() {
                    return 0;
                }

                @Override
                public boolean exists(@NonNull String s) throws IOException {
                    return true;
                }
            };
        }
    }
}