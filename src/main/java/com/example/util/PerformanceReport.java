package com.example.util;

import javax.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public class PerformanceReport {
    private final Statistics stats;

    public PerformanceReport(EntityManagerFactory emf) {
        this.stats = emf.unwrap(SessionFactory.class).getStatistics();
    }

    public void printReport() {
        long totalQueryMillis = 0;
        for (String query : stats.getQueries()) {
            totalQueryMillis += stats.getQueryStatistics(query).getExecutionTotalTime();
        }
        System.out.println("=== RAPPORT DE PERFORMANCE ===");
        System.out.println("Requêtes JPQL/Criteria : " + stats.getQueryExecutionCount());
        System.out.println("Instructions SQL préparées : " + stats.getPrepareStatementCount());
        System.out.println("Cache L2 hits : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Cache L2 misses : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Cache L2 puts : " + stats.getSecondLevelCachePutCount());
        System.out.println("Cache de requête hits : " + stats.getQueryCacheHitCount());
        System.out.println("Transactions : " + stats.getTransactionCount());
        System.out.println("Temps des requêtes (ms) : " + totalQueryMillis);
    }
}
