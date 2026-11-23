package com.projectrebirth.machinemind;

import java.util.UUID;

import javax.annotation.Nullable;

/** A machine of a node of the machine mind: it belongs to the node and goes dead with it. */
public interface MindUnit {
    @Nullable
    UUID node();

    void setNode(UUID node);

    /** Its node's computer is destroyed. */
    void shutDown();
}
