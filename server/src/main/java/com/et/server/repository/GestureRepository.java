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

    public String save(Gesture gesture) {
        if (findByName(gesture.getName()) == null) {
            em.persist(gesture);
            return gesture.getName();
        } else {
            em.merge(gesture);
            return gesture.getName();
        }
    }

    public Gesture findByName(String name) {
        return em.find(Gesture.class, name);
    }

    @SuppressWarnings("unchecked")
    public List<Gesture> findAll() {
        return em.createQuery("from Gesture").getResultList();
    }
}
