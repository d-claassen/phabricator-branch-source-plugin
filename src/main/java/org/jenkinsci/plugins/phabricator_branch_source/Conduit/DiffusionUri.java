package org.jenkinsci.plugins.phabricator_branch_source.Conduit;

import net.sf.json.JSONObject;

/**
 * Created by claassen on 11-01-17.
 */
public class DiffusionUri {
    private JSONObject uriObject;

    public DiffusionUri(JSONObject uriObject) {
        this.uriObject = uriObject;
    }

    public String getUri() {
        return this.uriObject
                .getJSONObject("fields")
                .getJSONObject("uri")
                .getString("effective");
    }

    public boolean isVisible() {
        return this.uriObject
                .getJSONObject("fields")
                .getJSONObject("display")
                .getString("effective") == "always";
    }
}
