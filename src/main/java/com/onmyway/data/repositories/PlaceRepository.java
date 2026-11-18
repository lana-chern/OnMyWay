package com.onmyway.data.repositories;

import com.onmyway.data.entities.Place;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("""
            select p from Place p
            join p.publishedRevision r
            where p.city.id = :cityId
              and r.status = com.onmyway.data.entities.PlaceRevisionStatus.PUBLISHED
              and (:query is null
                   or lower(r.name) like lower(concat('%', :query, '%')) escape '!'
                   or lower(coalesce(r.description, '')) like lower(concat('%', :query, '%')) escape '!')
            order by r.name asc, p.id asc
            """)
    Page<Place> searchPublished(@Param("cityId") Long cityId,
                                @Param("query") String query,
                                Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Place p where p.id = :id")
    Optional<Place> findByIdForUpdate(@Param("id") Long id);
}
