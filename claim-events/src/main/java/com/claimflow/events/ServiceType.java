package com.claimflow.events;

/** Kinds of medical service a claim can be for. A plan covers a subset of them. */
public enum ServiceType {
    PRIMARY_CARE,
    SPECIALIST,
    LAB,
    IMAGING,
    PHARMACY,
    EMERGENCY
}
