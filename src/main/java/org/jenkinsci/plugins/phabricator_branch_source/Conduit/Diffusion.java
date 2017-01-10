package org.jenkinsci.plugins.phabricator_branch_source.Conduit;

import java.util.ArrayList;

/**
 *
 */
public class Diffusion {
    private String name;
    private String phid;
    private String vcs;
    private ArrayList<DiffusionUri> uris;

    public Diffusion(String name, String phid, String vcs, ArrayList<DiffusionUri> uris) {
        this.name = name;
        this.phid = phid;
        this.vcs = vcs;
        this.uris = uris;
    }

    public String getName() {
        return name;
    }

    public String getPhid() {
        return phid;
    }

    public String getVcs() {
        return vcs;
    }

    public String getPrimaryUrl() {
        for (DiffusionUri uri : uris) {
            if(uri.isVisible()) {
                return uri.getUri();
            }
        }
        return uris.get(0).getUri();
    }
}
