package com.example;

import static org.junit.jupiter.api.Assertions.*;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.service.ReservationConflictException;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReservationTest {
    private static final String URL = "jdbc:h2:mem:reservationdb;MODE=LEGACY;DB_CLOSE_DELAY=-1";
    private static EntityManagerFactory emf;
    private static DataInitializer.Seed seed;
    private static ReservationService service;

    @BeforeAll static void init() throws Exception {
        MigrationExecutor migrations = new MigrationExecutor(URL, "sa", "");
        migrations.executeAll();
        migrations.executeAll(); // replay must be safe
        emf = Persistence.createEntityManagerFactory("ReservationPU");
        EntityManager em = emf.createEntityManager();
        try { seed = new DataInitializer(em).initData(); }
        finally { em.close(); }
        service = new ReservationService(emf);
    }

    @AfterAll static void close() { if (emf != null) emf.close(); }

    @Test void migrationAndFixture() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement statement = conn.createStatement();
             ResultSet rows = statement.executeQuery("SELECT description FROM salles WHERE id = " + seed.salles.get(0))) {
            assertTrue(rows.next());
            assertEquals("Projecteur et visioconférence", rows.getString(1));
        }
        assertEquals(3, seed.utilisateurs.size());
        assertEquals(3, seed.salles.size());
        assertEquals(3, seed.reservations.size());
    }

    @Test void createUpdateDelete() {
        LocalDateTime start = seed.base.plusDays(4);
        Reservation created = service.creer(seed.utilisateurs.get(0), seed.salles.get(2), start, start.plusHours(1));
        assertNotNull(created.getId());
        assertEquals(start, service.trouverParId(created.getId()).getDateDebut());
        Reservation updated = service.modifier(created.getId(), created.getVersion(), start.plusHours(1), start.plusHours(2));
        assertEquals(start.plusHours(1), updated.getDateDebut());
        assertTrue(updated.getVersion() > created.getVersion());
        service.supprimer(updated.getId(), updated.getVersion());
        assertNull(service.trouverParId(created.getId()));
    }

    @Test void overlapRejectedButAdjacentSlotAllowed() {
        assertThrows(ReservationConflictException.class, () -> service.creer(
            seed.utilisateurs.get(1), seed.salles.get(0), seed.base.plusMinutes(30), seed.base.plusHours(1)));
        Reservation adjacent = service.creer(seed.utilisateurs.get(1), seed.salles.get(0),
            seed.base.plusHours(2), seed.base.plusHours(3));
        service.supprimer(adjacent.getId(), adjacent.getVersion());
    }

    @Test void invalidDatesAndMissingRelationsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.creer(
            seed.utilisateurs.get(0), seed.salles.get(0), seed.base, seed.base));
        assertThrows(IllegalArgumentException.class, () -> service.creer(
            -1L, seed.salles.get(0), seed.base.plusDays(5), seed.base.plusDays(5).plusHours(1)));
    }

    @Test void jpqlAndCriteriaReturnSameResults() {
        LocalDateTime after = seed.base.minusDays(1);
        assertEquals(2, service.listerParSalleEtDateJPQL("Salle A", after).size());
        assertEquals(2, service.listerParSalleEtDateCriteria("Salle A", after).size());
        assertEquals(service.listerParSalleEtDateJPQL("Salle A", after).get(0).getId(),
            service.listerParSalleEtDateCriteria("Salle A", after).get(0).getId());
    }

    @Test void staleVersionIsRejected() {
        LocalDateTime start = seed.base.plusDays(6);
        Reservation created = service.creer(seed.utilisateurs.get(0), seed.salles.get(2), start, start.plusHours(1));
        int staleVersion = service.trouverParId(created.getId()).getVersion();
        Reservation updated = service.modifier(created.getId(), staleVersion, start.plusHours(1), start.plusHours(2));
        assertThrows(OptimisticLockException.class, () -> service.modifier(
            created.getId(), staleVersion, start.plusHours(2), start.plusHours(3)));
        service.supprimer(updated.getId(), updated.getVersion());
    }

    @Test void jpaVersionDetectsTwoConcurrentEditors() {
        LocalDateTime start = seed.base.plusDays(7);
        Reservation created = service.creer(seed.utilisateurs.get(0), seed.salles.get(2), start, start.plusHours(1));
        EntityManager first = emf.createEntityManager();
        EntityManager second = emf.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Reservation edit1 = first.find(Reservation.class, created.getId());
            Reservation edit2 = second.find(Reservation.class, created.getId());
            edit1.setDateFin(start.plusHours(2));
            first.getTransaction().commit();
            edit2.setDateFin(start.plusHours(3));
            RuntimeException failure = assertThrows(RuntimeException.class, () -> second.getTransaction().commit());
            assertTrue(hasOptimisticCause(failure), "Expected an optimistic lock failure, got " + failure);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
            Reservation latest = service.trouverParId(created.getId());
            service.supprimer(latest.getId(), latest.getVersion());
        }
    }

    private static boolean hasOptimisticCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException ||
                cause instanceof org.hibernate.StaleObjectStateException) return true;
        }
        return false;
    }

    @Test void secondLevelCacheWorksAcrossEntityManagers() {
        Long id = seed.salles.get(0);
        emf.getCache().evict(Salle.class, id);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        EntityManager first = emf.createEntityManager();
        try { assertEquals("Salle A", first.find(Salle.class, id).getNom()); }
        finally { first.close(); }
        assertEquals(1, stats.getPrepareStatementCount());
        stats.clear();
        EntityManager second = emf.createEntityManager();
        try { assertEquals("Salle A", second.find(Salle.class, id).getNom()); }
        finally { second.close(); }
        assertEquals(0, stats.getPrepareStatementCount());
        assertTrue(stats.getSecondLevelCacheHitCount() >= 1);
    }
}
