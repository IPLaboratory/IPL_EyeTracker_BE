package com.et.server.repository;

import com.et.server.Device;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeviceRepository {

    @PersistenceContext
    EntityManager em;

    public Long save(Device device) {
        em.persist(device);
        return device.getId();
    }

    public Device findById(Long id) {
        return em.find(Device.class, id);
    }

    @SuppressWarnings("unchecked")
    public List<Device> findAll(Long homeId) {
        return em.createQuery("from Device m where m.home.id = :homeId")
                .setParameter("homeId", homeId)
                .getResultList();
    }

    public Device findByName(String deviceName) {
        List<Device> result = em.createQuery("from Device d where d.name = :deviceName", Device.class)
                .setParameter("deviceName", deviceName)
                .getResultList();

        return result.isEmpty() ? null : result.get(0);  // 조회 결과가 없으면 null 반환
    }
}
