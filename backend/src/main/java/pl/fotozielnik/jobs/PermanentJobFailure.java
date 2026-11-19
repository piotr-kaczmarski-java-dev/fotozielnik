package pl.fotozielnik.jobs;

/** Thrown by a {@link JobHandler} when retrying cannot help (e.g. a corrupt file): the job fails at once. */
public class PermanentJobFailure extends Exception {

    public PermanentJobFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
