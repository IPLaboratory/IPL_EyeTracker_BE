package com.et.server;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Gesture {

    @Id
    private String name;  // Primary Key로 설정
    private String photoPath;
    private String description;

    public Gesture() {
    }

    public Gesture(String name, String photoPath, String description) {
        this.name = name;
        this.photoPath = photoPath;
        this.description = description;
    }
}
