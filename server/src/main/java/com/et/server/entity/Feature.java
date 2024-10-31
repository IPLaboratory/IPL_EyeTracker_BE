package com.et.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
// 해당 레코드에 값이 2개 이상 존재하지 않도록 보장 -> 각 기기 당 하나의 제스처만 선택 가능
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"device_id", "gesture_id"})})
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "device_id")
    private Device device;

    private String name;
    private String ir;  // 아두이노 통신

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "gesture_name")
    private Gesture gesture;

    public Feature() {
    }

    public Feature(Long id, Device device, Gesture gesture, String ir, String name) {
        this.id = id;
        this.device = device;
        this.name = name;
        this.ir = ir;
        this.gesture = gesture;
    }
}
