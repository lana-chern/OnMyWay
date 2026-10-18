package com.onmyway.data.repositories;

import com.onmyway.data.entities.Place;
import com.onmyway.data.entities.PlaceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    @Query("""
            select p from Place p
            where p.city.id = :cityId
              and p.status = :status
              and (:query is null
                   or lower(p.name) like lower(concat('%', :query, '%')) escape '!'
                   or lower(coalesce(p.description, '')) like lower(concat('%', :query, '%')) escape '!')
            order by p.name asc, p.id asc
            """)
    Page<Place> searchPublished(@Param("cityId") Long cityId,
                                @Param("status") PlaceStatus status,
                                @Param("query") String query,
                                Pageable pageable);

    List<Place> findByStatusOrderByCreatedAtAsc(PlaceStatus status);
}
