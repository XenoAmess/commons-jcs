
package org.apache.jcs.utils.locking;

/**
 * Used to keep track of the total number of outstanding locks placed but not
 * yet released for a given resource.
 *
 * @author asmuts
 * @created January 15, 2002
 */
class RwLockHolder
{
    private final static long UNUSED_TIME = 10 * 1000;
    // 10 seconds.

    /** Read/Write lock for a specific resource. */
    final ReadWriteLock rwlock;

    /**
     * Number of locks that have been placed on the rwlock and not yet released.
     */
    int lcount = 1;

    /** Last timestamp when the lcount was zero. */
    long lastInactiveTime = -1;


    /**
     * Constructs with a Read/Write lock for a specific resource.
     *
     * @param rwlock
     */
    RwLockHolder( ReadWriteLock rwlock )
    {
        this.rwlock = rwlock;
    }


    /**
     * Returns true iff this object satisfies the condition of removing
     * RwLockHolder from the managing ReadWriteLockManager.
     */
    boolean removable( long now )
    {
        return lcount == 0 && lastInactiveTime > 0 && now - lastInactiveTime > UNUSED_TIME;
    }
}
