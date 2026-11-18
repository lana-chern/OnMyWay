package com.onmyway.data.repositories;

import com.onmyway.data.entities.PlaceRevision;
import com.onmyway.data.entities.PlaceRevisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaceRevisionRepository extends JpaRepository<PlaceRevision, Long> {
    Optional<PlaceRevision> findFirstByPlaceIdAndStatusInOrderByCreatedAtDesc(
            Long placeId, Collection<PlaceRevisionStatus> statuses);

    Optional<PlaceRevision> findByPlaceIdAndStatus(Long placeId, PlaceRevisionStatus status);

    List<PlaceRevision> findByStatusOrderByCreatedAtAsc(PlaceRevisionStatus status);
}
