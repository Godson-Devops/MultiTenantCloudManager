package com.portal.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StatusTest {

    @ParameterizedTest
    @EnumSource(Status.class)
    @DisplayName("every status round-trips through its own code")
    void roundTripsThroughCode(Status status) {
        assertSame(status, Status.fromCode(status.code()));
    }

    @Test
    @DisplayName("codes are the lowercase words stored in the database and sent to the browser")
    void codesAreLowercase() {
        assertEquals("creating", Status.CREATING.code());
        assertEquals("running", Status.RUNNING.code());
        assertEquals("stopped", Status.STOPPED.code());
        assertEquals("error", Status.ERROR.code());
        assertEquals("deleted", Status.DELETED.code());
    }

    @Test
    @DisplayName("an unknown stored value reads as an error, never as a guess")
    void unknownValueBecomesError() {
        assertSame(Status.ERROR, Status.fromCode("migrating"));
        assertSame(Status.ERROR, Status.fromCode("RUNNING"));
        assertSame(Status.ERROR, Status.fromCode(""));
        assertSame(Status.ERROR, Status.fromCode(null));
    }

    @Test
    @DisplayName("Nova server states collapse onto the vocabulary the UI renders")
    void mapsOpenStackStates() {
        assertSame(Status.CREATING, Status.fromOpenStack("BUILD"));
        assertSame(Status.RUNNING, Status.fromOpenStack("ACTIVE"));
        assertSame(Status.STOPPED, Status.fromOpenStack("SHUTOFF"));
        assertSame(Status.ERROR, Status.fromOpenStack("REBUILD"));
        assertSame(Status.ERROR, Status.fromOpenStack("PAUSED"));
        assertSame(Status.ERROR, Status.fromOpenStack(null));
    }

    @Test
    @DisplayName("a Kubernetes phase that has not settled yet is still creating")
    void mapsPodPhases() {
        assertSame(Status.RUNNING, Status.fromPodPhase("Running"));
        assertSame(Status.ERROR, Status.fromPodPhase("Failed"));
        assertSame(Status.STOPPED, Status.fromPodPhase("Succeeded"));
        assertSame(Status.CREATING, Status.fromPodPhase("Pending"));
        assertSame(Status.CREATING, Status.fromPodPhase("Unknown"));
        assertSame(Status.CREATING, Status.fromPodPhase(null));
    }
}
