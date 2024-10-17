package com.et.server.repository;

import com.et.server.Gesture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GestureRepository {

    @PersistenceContext
    private EntityManager em;

    public Long save(Gesture gesture) {
        if (gesture.getId() == null) {
            em.persist(gesture);
            return gesture.getId();
        } else {
            em.merge(gesture);
            return gesture.getId();
        }
    }

    public Gesture findById(Long id) {
        return em.find(Gesture.class, id);
    }

    @SuppressWarnings("unchecked")
    public List<Gesture> findAll() {
        return em.createQuery("from Gesture").getResultList();
    }
}
