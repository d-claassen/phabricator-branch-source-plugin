package org.jenkinsci.plugins.phabricator_branch_source.Conduit;

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMSourceRequest;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.phabricator_branch_source.BranchSCMHead;
import org.jenkinsci.plugins.phabricator_branch_source.PhabricatorSCMSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 */
public class DiffusionClient {
    private final ConduitAPIClient conduit;

    public DiffusionClient(ConduitAPIClient conduit) {
        this.conduit = conduit;
    }

    public ArrayList<Diffusion> getActiveRepositories() throws IOException, ConduitAPIException {
        JSONObject attachments = new JSONObject();
        attachments.element("uris", true);

        JSONObject params = new JSONObject();
        params.element("order", "name")
                .element("queryKey", "active")
                .element("attachments", attachments);

        JSONObject response = conduit.perform("diffusion.repository.search", params);

        return getDiffusionsFromResponse(response);
    }

    public Diffusion getRepository(String repository) throws IOException, ConduitAPIException {
        JSONObject attachments = new JSONObject();
        attachments.element("uris", true);

        JSONArray phids = new JSONArray();
        phids.add(repository);
        JSONObject constraints = new JSONObject();
        constraints.element("phids", phids);

        JSONObject params = new JSONObject();
        params.element("constraints", constraints)
                .element("attachments", attachments);

        JSONObject response = conduit.perform("diffusion.repository.search", params);

        ArrayList<Diffusion> diffusions = getDiffusionsFromResponse(response);
        return diffusions.get(0);
    }

    private ArrayList<Diffusion> getDiffusionsFromResponse(JSONObject response) {
        ArrayList<Diffusion> diffusions = new ArrayList<>();
        boolean hasResult = response.has("result") && !response.getJSONObject("result").isNullObject();
        if (hasResult && response.getJSONObject("result").has("data")) {
            JSONArray repositories = response.getJSONObject("result").getJSONArray("data");

            for (int i = 0; i < repositories.size(); i++) {
                JSONObject repo = repositories.getJSONObject(i);
                JSONObject attachments = repo.getJSONObject("attachments");
                ArrayList<DiffusionUri> uris = new ArrayList<>();
                if(attachments.has("uris") && attachments.getJSONObject("uris").has("uris")) {
                    JSONArray uriObjects = attachments.getJSONObject("uris").getJSONArray("uris");
                    for(int ui = 0; ui < uriObjects.size(); ui++) {
                        uris.add(new DiffusionUri(uriObjects.getJSONObject(ui)));
                    }
                }

                JSONObject fields = repo.getJSONObject("fields");
                Diffusion diff = new Diffusion(
                        fields.getString("name"),
                        repo.getString("phid"),
                        fields.getString("vcs"),
                        uris
                );

                diffusions.add(diff);
            }
        }
        return diffusions;
    }

    public ArrayList<PhabricatorBranch> getBranches(String repository) throws IOException, ConduitAPIException {
        ArrayList<PhabricatorBranch> branches = new ArrayList<>();

        JSONObject params = new JSONObject();
        params.element("closed", false);
        params.element("repository", repository);

        JSONObject branchesResponse = conduit.perform("diffusion.branchquery", params);
        if(!branchesResponse.has("result")) {
            return branches;
        }

        JSONArray openBranches = branchesResponse.getJSONArray("result");
        int nrOpenBranches = openBranches.size();
        Integer i = 0;
        for (; i < nrOpenBranches; i++) {
            JSONObject branch = openBranches.getJSONObject(i);
            branches.add(
                    new PhabricatorBranch(branch.getString("shortName"),
                            branch.getString("commitIdentifier"),
                            branch.getJSONObject("rawFields").getInt("epoch")
                            )
            );
        }
        return branches;
    }
}
