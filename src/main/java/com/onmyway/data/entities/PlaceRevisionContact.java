package com.onmyway.data.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "place_revision_contacts")
public class PlaceRevisionContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    private PlaceRevision revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceContactType type;

    @Column(name = "contact_value", nullable = false)
    private String value;
}
