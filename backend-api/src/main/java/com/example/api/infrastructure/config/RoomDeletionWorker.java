package com.example.api.infrastructure.config;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import io.quarkus.runtime.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class RoomDeletionWorker {
  @Inject FirebaseProvider firebase;
  private ScheduledExecutorService worker;

  void start(@Observes StartupEvent event) {
    worker =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var t = new Thread(r, "room-deletion-worker");
              t.setDaemon(true);
              return t;
            });
    worker.scheduleWithFixedDelay(this::run, 2, 2, TimeUnit.SECONDS);
  }

  void stop(@Observes ShutdownEvent event) {
    if (worker != null) {
      worker.shutdownNow();
      try {
        worker.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void run() {
    try {
      var db = firebase.firestore();
      for (var job :
          db.collection("roomDeletionJobs")
              .whereIn("status", List.of("PENDING", "RUNNING"))
              .limit(20)
              .get()
              .get(5, TimeUnit.SECONDS)
              .getDocuments()) {
        String id = job.getId();
        if (!id.matches("[a-f0-9-]{36}")) continue;
        boolean claimed =
            db.runTransaction(
                    tx -> {
                      var current = tx.get(job.getReference()).get();
                      var lease = current.getTimestamp("leaseUntil");
                      if ("RUNNING".equals(current.getString("status"))
                          && lease != null
                          && lease.compareTo(Timestamp.now()) > 0) return false;
                      tx.update(
                          job.getReference(),
                          Map.of(
                              "status",
                              "RUNNING",
                              "leaseUntil",
                              Timestamp.ofTimeSecondsAndNanos(
                                  java.time.Instant.now().plusSeconds(30).getEpochSecond(), 0)));
                      return true;
                    })
                .get(5, TimeUnit.SECONDS);
        if (!claimed) continue;
        try {
          var ref = db.collection("rooms").document(id);
          var room = ref.get().get(5, TimeUnit.SECONDS);
          if (room.exists() && !"DELETING".equals(room.getString("status"))) continue;
          erase(ref.collection("messages"));
          erase(ref.collection("members"));
          var batch = db.batch();
          batch.delete(ref);
          batch.update(
              job.getReference(),
              Map.of("status", "DONE", "updatedAt", FieldValue.serverTimestamp()));
          batch.commit().get(10, TimeUnit.SECONDS);
        } catch (Exception failure) {
          job.getReference().update("status", "PENDING").get(5, TimeUnit.SECONDS);
        }
      }
    } catch (Exception failure) {
      if (failure instanceof InterruptedException)
        Thread.currentThread()
            .interrupt(); /* Missing credentials/dependency: next bounded cycle retries; readiness reports the failure. */
    }
  }

  private void erase(CollectionReference col) throws Exception {
    while (true) {
      if (Thread.currentThread().isInterrupted())
        throw new InterruptedException("Limpieza interrumpida; conservar job");
      var docs = col.limit(200).get().get(5, TimeUnit.SECONDS).getDocuments();
      if (docs.isEmpty()) return;
      var batch = firebase.firestore().batch();
      for (var doc : docs) batch.delete(doc.getReference());
      batch.commit().get(10, TimeUnit.SECONDS);
    }
  }
}
