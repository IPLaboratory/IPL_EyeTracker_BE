package com.et.server.repository;

import com.et.server.entity.Feature;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeatureRepository {

    @PersistenceContext
    EntityManager em;

    public Long save(Feature feature) {
        if (feature.getId() == null) {
            em.persist(feature);
        } else {
            em.merge(feature);
        }
        return feature.getId();
    }

    public void delete(Feature feature) {
        em.remove(feature);
    }

    public Feature findById(Long id) {
        return em.find(Feature.class, id);
    }

    @SuppressWarnings("unchecked")
    public List<Feature> findAll() {
        return em.createQuery("from Feature").getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Feature> findAllDeviceId(Long deviceId) {
        return em.createQuery("from Feature f where f.device.id = :deviceId")
                .setParameter("deviceId", deviceId)
                .getResultList();
    }
}
