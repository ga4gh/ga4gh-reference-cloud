package org.ga4gh.refcloud.api.compute.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.springframework.core.io.Resource;

// where a connection's runs keep their files (D15, D18): a directory on a shared filesystem, or a prefix in an S3
// bucket (spec 0012). a run's location (compute_run.run_directory) is a path for the first and s3://<bucket>/<key> for
// the second. WES puts the run's workflow and staged inputs there, the engine and executor work there, and WES reads
// the outputs and logs back. the layout under a run's location:
//
//   workflow/                    the workflow, on a shared filesystem   } deleted when the run ends
//   engine/workflow.tar.gz       the workflow, on S3                    }
//   inputs/                      staged DRS inputs                      }
//   work/                        the engine's work directory            }
//   outputs/                     workflow outputs                         kept
//   engine.stdout.log ...        the engine's logs: in the run directory on a shared filesystem, under engine/ on S3
//
// one instance per connection (RunStorages); every method takes the run's location
public interface RunStorage {

    ComputeConfig.StorageType type();

    // a new run's location. a shared filesystem creates its directory, with inputs/, work/ and outputs/
    String allocate(String runId) throws IOException;

    // an empty local directory for the run's workflow: the run's workflow/ itself on a shared filesystem, a temporary
    // directory for S3. follow it with publishWorkflow once the workflow is complete, or discardWorkflowStaging
    Path workflowStaging(String location) throws IOException;

    // the workflow is complete in its staging directory: S3 uploads it as engine/workflow.tar.gz and deletes the
    // staging directory; on a shared filesystem it's already in place
    void publishWorkflow(String location, Path staging) throws IOException;

    // preparation failed: drop a staging directory that isn't part of the run (S3's temporary one)
    void discardWorkflowStaging(String location, Path staging);

    // stores one staged input at inputs/<relativePath> from content, which must come to expectedSize bytes when that's
    // known (nothing is left behind if it doesn't), and returns where the engine finds it: a path, or an s3:// URI
    String stageInput(String location, String relativePath, InputStream content, Long expectedSize) throws IOException;

    // the run's outputs: regular files under outputs/, sorted by path
    List<StoredFile> listOutputs(String location) throws IOException;

    // one output, by its path relative to outputs/; empty if there's no such file (or the path tries to leave outputs/)
    Optional<Resource> openOutput(String location, String relativePath) throws IOException;

    // one of the engine's log files (e.g. engine.stdout.log); empty until the engine writes it
    Optional<Resource> openEngineLog(String location, String name) throws IOException;

    // the engine's own small files, by name (its config, params, exit code): in the run directory on a shared
    // filesystem, under engine/ on S3, where the engine reads and writes them too
    void writeEngineFile(String location, String name, String content) throws IOException;

    // empty until the file exists
    Optional<String> readEngineFile(String location, String name) throws IOException;

    // up to the last bytes of an engine file (e.g. the end of nextflow.log); empty text if it doesn't exist
    String readEngineFileTail(String location, String name, long bytes) throws IOException;

    // temporary credentials for one run's engine, limited to the run's own location and lasting lifetime (decision 3);
    // empty where the engine needs none (a shared filesystem, where the engine mounts only its run's directory)
    Optional<RunCredentials> issueRunCredentials(String location, Duration lifetime);

    // removes what an ended run no longer needs: its workflow, staged inputs and work directory (and, on a shared
    // filesystem, Nextflow's cache in the run directory). failures are logged, not thrown
    void removeIntermediates(String location);

    // why runs can't use this storage right now, if anything (the connection's health check, D10)
    Optional<String> check();
}
