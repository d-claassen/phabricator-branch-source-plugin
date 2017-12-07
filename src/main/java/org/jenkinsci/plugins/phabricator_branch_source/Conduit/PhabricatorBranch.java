package org.jenkinsci.plugins.phabricator_branch_source.Conduit;

import javax.annotation.Nonnull;

public class PhabricatorBranch {
    private final String name;
    private long epoch;
    private String hash;

    public PhabricatorBranch(@Nonnull String name, String hash, long epoch) {
        this.name = name;
        this.hash = hash;
        this.epoch = epoch;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public String getName() {
        return name;
    }
}
