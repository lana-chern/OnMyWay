package com.onmyway.data.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;


@Data
@NoArgsConstructor
@Entity
public class District {
    @Id
    @GeneratedValue()
    private Long id;

    private String name;

    public District(String name) {
        this.name = name;
    }
}
