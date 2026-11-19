package pl.fotozielnik.photos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes the owner: there is deliberately no way to load "any user's" photo by id. */
interface PhotoRepository extends JpaRepository<Photo, UUID> {

    Optional<Photo> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Photo> findByOwnerIdAndSha256(UUID ownerId, String sha256);

    @Query("select p.sha256 from Photo p where p.ownerId = :owner and p.sha256 in :hashes")
    List<String> findExistingHashes(@Param("owner") UUID ownerId, @Param("hashes") List<String> hashes);

    @Query("select p from Photo p where p.ownerId = :owner order by p.sortTime desc, p.id desc")
    List<Photo> findFirstPage(@Param("owner") UUID ownerId, Limit limit);

    /** Keyset pagination: the page after (sortTime, id), stable while new photos arrive. */
    @Query("""
            select p from Photo p
            where p.ownerId = :owner
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

    @org.springframework.data.jpa.repository.Modifying
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

    interface StateCount {
        ProcessingState getState();

        long getCount();
    }
}
