package jesync.core;

/**
 * Defines a lock and all the logic associated with granting and releasing them
 *
 * @author Julio Viera <julio.viera@gmail.com>
 */
public class Lock {

    private String lockKey;
    private LockRequestList lockRequests = new LockRequestList();
    private LockRequestList locksGranted = new LockRequestList();
    private int lockedMaxConcurrent = Integer.MAX_VALUE;

    public String getLockKey() {
        return lockKey;
    }

    Lock(String lockKey) {
        this.lockKey = lockKey;
        this.lockRequests = new LockRequestList();
    }

    /**
     * Process the lock request
     *
     * @param request
     */
    public final synchronized void requestLock(LockRequest request) {
        if (this.locksGranted.contains(request)) {
            //this lock is already granted for this request, lets re-grant it
            this.grantLock(request);
        } else {
            this.lockRequests.add(request);
            this.processRequestList();
        }
    }

    /**
     * This function loops trough all the requests and grants the lock checking
     * that the request entering the lock accepts the number of concurrent
     * locks. Also it will respect the max number of concurrent locks for a
     * particular lock.
     */
    private void processRequestList() {
        int newSize = locksGranted.size() + 1;

        for (int i = 0; i < this.lockRequests.size(); i++) {
            LockRequest req = this.lockRequests.get(i);
            if (req.getMaxConcurrent() >= newSize && newSize <= this.lockedMaxConcurrent) {
                this.grantLock(req);
                newSize++;
                i--;
                continue;
            }
        }

    }

    /**
     * Grants a lock. This function assumes that all the conditions to grant a
     * lock are met. It shifts the request from the request list to the granted
     * list. Also the callback "lockGranted" is done from within this function.
     *
     * @param request
     */
    private void grantLock(LockRequest request) {
        if (!this.locksGranted.contains(request)) { //We have never granted this lock to this request.
            this.locksGranted.add(request);

            //Recalculate the lockedMaxConcurrent to respect the maxConcurrent
            //values for the already locked requests
            if (this.lockedMaxConcurrent > request.getMaxConcurrent()) {
                this.lockedMaxConcurrent = request.getMaxConcurrent();
            }
        }
        this.lockRequests.remove(request);

        //Create the handle that will be used by the request to release the lock
        LockHandle handle = new LockHandle(this, request);

        //Callback
        request.lockGranted(handle, this.locksGranted.size());
    }

    /**
     * Releases the lock. This function removes the request from the granted
     * list. It also checks if we need to recalculate lockedMaxConcurrent, in
     * case we do it triggers the recalculation
     *
     * @param request
     * @return Returns the number of concurrent request that where using the
     * lock before it was released by this call
     */
    int releaseLock(LockRequest request) {
        int executing = this.locksGranted.size();
        if (this.locksGranted.remove(request)) {
            if (request.getMaxConcurrent() == this.lockedMaxConcurrent) {
                this.buildLockedMaxConcurrent();
            }
            this.processRequestList();
        }
        return executing;
    }

    /**
     * Builds the lockedMaxConcurrent value. Loops through the locksGranted list
     * and saves the minimum maxConcurrent among all the requests
     */
    private void buildLockedMaxConcurrent() {
        this.lockedMaxConcurrent = Integer.MAX_VALUE;
        for (int i = 0; i < this.locksGranted.size(); i++) {
            LockRequest req = this.locksGranted.get(i);
            if (req.getMaxConcurrent() < this.lockedMaxConcurrent) {
                this.lockedMaxConcurrent = req.getMaxConcurrent();
            }
        }

    }
}
