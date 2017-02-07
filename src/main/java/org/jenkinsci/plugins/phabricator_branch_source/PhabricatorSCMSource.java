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
import hudson.util.ListBoxModel;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.Diffusion;
import org.jenkinsci.plugins.phabricator_branch_source.Conduit.DiffusionClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * SCM source implementation for Phabricator.
 *
 * It provides a way to discover/retrieve branches and Differential Revisions through the Phabricator API.
 * This might potentially be faster than the plain GIT SCM source implementation.
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

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer, @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener) throws IOException, InterruptedException {
        ConduitCredentials credentials = ConduitCredentialsDescriptor.getCredentials(null, phabCredentialsId);
        ConduitAPIClient client = new ConduitAPIClient(credentials.getUrl(), credentials.getToken().getPlainText());

        listener.getLogger().format("Connecting to %s with credentials%n", credentials.getUrl());

        retrieveBranches(client, observer, listener);

        retrieveDifferentialRevisions(client, observer, listener);
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

    private void observe(SCMHeadObserver observer, TaskListener listener, SCMHead head, SCMRevision revision) {
        listener.getLogger().format("%nStart observing now...%n");

        observer.observe(head, revision);
    }

    private void observe(SCMHeadObserver observer, TaskListener listener, String repositoryUrl, String branchName, String hash, String baseBranchName, @Nullable Integer revisionId) {
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
            String refspec = "+refs/heads/" + h.getName() + ":refs/remotes/origin/" + h.getName();
            String refspec1 = "+refs/heads/*:refs/remotes/origin/*";
            result.add(new UserRemoteConfig(h.getRepoUrl(), "origin", refspec1, repoCredentialsId));
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

    }
}