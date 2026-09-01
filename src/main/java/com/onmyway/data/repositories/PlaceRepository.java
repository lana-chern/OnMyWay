package com.onmyway.data.repositories;

import com.onmyway.data.entities.Place;
import com.onmyway.data.entities.PlaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    List<Place> findByCityIdAndStatus(Long cityId, PlaceStatus status);
}
