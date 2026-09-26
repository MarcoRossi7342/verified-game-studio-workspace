package dev.learning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WorkspaceJoinTest {
    @Test
    void onlyCompanyEmailMayJoinVerifiedWorkspace() {
        assertTrue(WorkspaceJoin.eligible("studio.example", "artist@studio.example"));
        assertFalse(WorkspaceJoin.eligible("studio.example", "artist@other.example"));
        assertFalse(WorkspaceJoin.eligible("studio.example", "artist@studio.example.evil"));
    }
}
