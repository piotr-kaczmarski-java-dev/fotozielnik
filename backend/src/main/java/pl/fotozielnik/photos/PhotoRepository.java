package pl.fotozielnik.photos;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes the owner: there is deliberately no way to load "any user's" photo by id. */
interface PhotoRepository extends JpaRepository<Photo, UUID> {

    Optional<Photo> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Photo> findByOwnerIdAndSha256(UUID ownerId, String sha256);

    @Query("select p.sha256 from Photo p where p.ownerId = :owner and p.sha256 in :hashes")
    List<String> findExistingHashes(@Param("owner") UUID ownerId, @Param("hashes") List<String> hashes);

    @Query("select p from Photo p where p.ownerId = :owner and p.deletedAt is null order by p.sortTime desc, p.id desc")
    List<Photo> findFirstPage(@Param("owner") UUID ownerId, Limit limit);

    /** Keyset pagination: the page after (sortTime, id), stable while new photos arrive. */
    @Query("""
            select p from Photo p
            where p.ownerId = :owner and p.deletedAt is null
              and (p.sortTime < :sortTime or (p.sortTime = :sortTime and p.id < :id))
            order by p.sortTime desc, p.id desc""")
    List<Photo> findPageAfter(
            @Param("owner") UUID ownerId, @Param("sortTime") Instant sortTime, @Param("id") UUID id, Limit limit);

    @Query("""
            select p.processingState as state, count(p) as count from Photo p
            where p.ownerId = :owner and p.importBatchId = :batch
            group by p.processingState""")
    List<StateCount> countByState(@Param("owner") UUID ownerId, @Param("batch") UUID batchId);

    // Location is a PostGIS geography column, written and read with native SQL and deliberately not mapped on
    // the entity (no hibernate-spatial; entity flushes never touch it).

    @Modifying
    @Query(value = """
                    update photo set location = ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                                     location_source = :source
                    where id = :id""", nativeQuery = true)
    void setLocation(
            @Param("id") UUID id, @Param("lat") double lat, @Param("lon") double lon, @Param("source") String source);

    @Query(value = """
                    select ST_Y(location::geometry) as latitude, ST_X(location::geometry) as longitude,
                           location_source as source
                    from photo where id = :id and owner_id = :owner and location is not null""", nativeQuery = true)
    Optional<LocationView> findLocation(@Param("id") UUID id, @Param("owner") UUID ownerId);

    interface LocationView {
        double getLatitude();

        double getLongitude();

        String getSource();
    }

    @Query("""
            select p.importBatchId as batchId, p.processingState as state, count(p) as count from Photo p
            where p.ownerId = :owner and p.importBatchId in :batches
            group by p.importBatchId, p.processingState""")
    List<BatchStateCount> countByStateForBatches(
            @Param("owner") UUID ownerId, @Param("batches") Collection<UUID> batchIds);

    interface BatchStateCount {
        UUID getBatchId();

        ProcessingState getState();

        long getCount();
    }

    // ---- trash (FR-022). Bulk updates: only the owner's rows match, so foreign ids are silently ignored.

    @Modifying
    @Query("""
            update Photo p set p.deletedAt = :now, p.updatedAt = :now
            where p.ownerId = :owner and p.id in :ids and p.deletedAt is null""")
    int moveToTrash(@Param("owner") UUID ownerId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Photo p set p.deletedAt = null, p.updatedAt = :now
            where p.ownerId = :owner and p.id in :ids and p.deletedAt > :purging""")
    int restore(
            @Param("owner") UUID ownerId,
            @Param("ids") Collection<UUID> ids,
            @Param("now") Instant now,
            @Param("purging") Instant purging);

    /** "Empty the trash": mark the photos for purging (deleted_at = {@code purging}); they vanish from the trash at once. */
    @Modifying
    @Query("""
            update Photo p set p.deletedAt = :purging, p.updatedAt = :now
            where p.ownerId = :owner and p.deletedAt > :purging""")
    int markForPurge(@Param("owner") UUID ownerId, @Param("now") Instant now, @Param("purging") Instant purging);

    /** Re-importing a photo that is in the trash brings it back. */
    @Modifying
    @Query("""
            update Photo p set p.deletedAt = null, p.updatedAt = :now
            where p.ownerId = :owner and p.sha256 in :hashes and p.deletedAt is not null""")
    int restoreByHashes(
            @Param("owner") UUID ownerId, @Param("hashes") Collection<String> hashes, @Param("now") Instant now);

    long countByOwnerIdAndDeletedAtAfter(UUID ownerId, Instant purging);

    @Query("""
            select p from Photo p where p.ownerId = :owner and p.deletedAt > :purging
            order by p.deletedAt desc, p.id desc""")
    List<Photo> findTrashFirstPage(@Param("owner") UUID ownerId, @Param("purging") Instant purging, Limit limit);

    @Query("""
            select p from Photo p
            where p.ownerId = :owner and p.deletedAt > :purging
              and (p.deletedAt < :deletedAt or (p.deletedAt = :deletedAt and p.id < :id))
            order by p.deletedAt desc, p.id desc""")
    List<Photo> findTrashPageAfter(
            @Param("owner") UUID ownerId,
            @Param("deletedAt") Instant deletedAt,
            @Param("id") UUID id,
            @Param("purging") Instant purging,
            Limit limit);

    /** Photos to purge: in the trash since before {@code cutoff}; {@code owner} null = everybody's. */
    @Query("""
            select p from Photo p
            where p.deletedAt is not null and p.deletedAt < :cutoff and (:owner is null or p.ownerId = :owner)
            order by p.deletedAt""")
    List<Photo> findTrashedBefore(@Param("cutoff") Instant cutoff, @Param("owner") @Nullable UUID ownerId, Limit limit);

    /** Deletes the row only if it is still in the trash (it may have been restored meanwhile). */
    @Modifying
    @Query("delete from Photo p where p.id = :id and p.deletedAt is not null")
    int deleteTrashed(@Param("id") UUID id);

    interface StateCount {
        ProcessingState getState();

        long getCount();
    }
}
