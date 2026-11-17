package com.onmyway.data.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "place_revision_photos")
public class PlaceRevisionPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    private PlaceRevision revision;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private Integer position;

    private String description;
}
