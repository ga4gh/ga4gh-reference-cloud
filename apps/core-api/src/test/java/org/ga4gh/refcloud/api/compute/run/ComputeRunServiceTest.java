package org.ga4gh.refcloud.api.compute.run;

import static org.testng.Assert.assertEquals;

import java.time.Instant;
import java.util.Optional;
import org.testng.annotations.Test;

public class ComputeRunServiceTest {

    private static ComputeRun run(Instant cancelRequestedAt) {
        return ComputeRun.builder().id("r").cancelRequestedAt(cancelRequestedAt).build();
    }

    @Test
    public void exitCodeDecidesTheOutcome() {
        assertEquals(ComputeRunService.outcome(run(null), 0, Optional.empty()), ComputeRunStates.COMPLETE);
        assertEquals(ComputeRunService.outcome(run(null), 1, Optional.empty()), ComputeRunStates.EXECUTOR_ERROR);
    }

    @Test
    public void engineThatNeverRanIsASystemError() {
        ComputeRun notFound = run(null);
        assertEquals(ComputeRunService.outcome(notFound, 127, Optional.empty()), ComputeRunStates.SYSTEM_ERROR);
        assertEquals(notFound.getStateReason(), "the engine could not be started (exit code 127)");
        assertEquals(ComputeRunService.outcome(run(null), 126, Optional.empty()), ComputeRunStates.SYSTEM_ERROR);
    }

    @Test
    public void cancelledRunIsCanceledHoweverItExited() {
        // nextflow exits non-zero on SIGTERM, and a killed engine exits 137
        assertEquals(ComputeRunService.outcome(run(Instant.now()), 143, Optional.empty()), ComputeRunStates.CANCELED);
        assertEquals(ComputeRunService.outcome(run(Instant.now()), 137, Optional.empty()), ComputeRunStates.CANCELED);
        assertEquals(ComputeRunService.outcome(run(Instant.now()), 0, Optional.empty()), ComputeRunStates.CANCELED);
    }

    @Test
    public void failureCausedByLosingTheRefcloudIsASystemError() {
        ComputeRun lostContact = run(null);
        String reason = "the workflow engine lost contact with the refcloud TES: ...";

        assertEquals(ComputeRunService.outcome(lostContact, 1, Optional.of(reason)), ComputeRunStates.SYSTEM_ERROR);
        assertEquals(lostContact.getStateReason(), reason);
        // a cancel still wins, and a success is a success
        assertEquals(ComputeRunService.outcome(run(Instant.now()), 1, Optional.of(reason)), ComputeRunStates.CANCELED);
        assertEquals(ComputeRunService.outcome(run(null), 0, Optional.of(reason)), ComputeRunStates.COMPLETE);
    }
}
