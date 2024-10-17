package com.et.server;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Gesture {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String photoPath;
    private String description;

    public Gesture() {
    }

    public Gesture(Long id, String photoPath, String description) {
        this.id = id;
        this.photoPath = photoPath;
        this.description = description;
    }
}
