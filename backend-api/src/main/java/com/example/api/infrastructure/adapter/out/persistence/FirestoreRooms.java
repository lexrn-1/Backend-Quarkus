package com.example.api.infrastructure.adapter.out.persistence;

import com.example.api.application.port.out.RoomRepository;
import com.example.api.domain.exception.DependencyUnavailableException;
import com.example.api.domain.room.*;
import com.example.api.infrastructure.config.FirebaseProvider;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FirestoreRooms implements RoomRepository {
  private static final Logger LOG = Logger.getLogger(FirestoreRooms.class);
  @Inject FirebaseProvider firebase;

  private CollectionReference collection() {
    return firebase.firestore().collection("rooms");
  }

  public Room create(Room room) {
    await(
        firebase
            .firestore()
            .runTransaction(
                tx -> {
                  var ref = collection().document(room.id());
                  tx.create(ref, data(room));
                  tx.create(
                      ref.collection("members").document(room.ownerUid()),
                      Map.of(
                          "uid",
                          room.ownerUid(),
                          "role",
                          "OWNER",
                          "joinedAt",
                          stamp(room.createdAt())));
                  return true;
                }));
    return room;
  }

  public Room find(String id) {
    return read(await(collection().document(id).get()));
  }

  public boolean member(String id, String uid) {
    return await(collection().document(id).collection("members").document(uid).get()).exists();
  }

  public Room join(String id, String uid) {
    return await(
        firebase
            .firestore()
            .runTransaction(
                tx -> {
                  var ref = collection().document(id);
                  var room = read(tx.get(ref).get());
                  room.active();
                  var member = ref.collection("members").document(uid);
                  var old = tx.get(member).get();
                  if (!old.exists())
                    tx.create(
                        member,
                        Map.of(
                            "uid",
                            uid,
                            "role",
                            room.ownerUid().equals(uid) ? "OWNER" : "MEMBER",
                            "joinedAt",
                            FieldValue.serverTimestamp()));
                  return room;
                }));
  }

  public Room update(String id, UnaryOperator<Room> action, boolean deletion) {
    return await(
        firebase
            .firestore()
            .runTransaction(
                tx -> {
                  var ref = collection().document(id);
                  var changed = action.apply(read(tx.get(ref).get()));
                  tx.set(ref, data(changed));
                  if (deletion)
                    tx.set(
                        firebase.firestore().collection("roomDeletionJobs").document(id),
                        Map.of(
                            "roomId",
                            id,
                            "status",
                            "PENDING",
                            "updatedAt",
                            FieldValue.serverTimestamp()));
                  return changed;
                }));
  }

  public Page list(String uid, boolean memberScope, int limit, String cursor) {
    Query query;
    if (memberScope)
      query =
          firebase
              .firestore()
              .collectionGroup("members")
              .whereEqualTo("uid", uid)
              .orderBy("joinedAt", Query.Direction.DESCENDING)
              .orderBy(FieldPath.documentId());
    else
      query =
          collection()
              .whereEqualTo("ownerUid", uid)
              .whereEqualTo("status", "ACTIVE")
              .orderBy("createdAt", Query.Direction.DESCENDING)
              .orderBy(FieldPath.documentId());
    if (cursor != null && !cursor.isBlank()) {
      try {
        String[] parts =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (parts.length != 5
            || !parts[0].equals(uid)
            || !parts[1].equals(memberScope ? "member" : "owned"))
          throw new IllegalArgumentException();
        String path = parts[4];
        if (!path.matches("rooms/[a-f0-9-]{36}" + (memberScope ? "/members/[^/]+" : "")))
          throw new IllegalArgumentException();
        query =
            query.startAfter(
                Timestamp.ofTimeSecondsAndNanos(
                    Long.parseLong(parts[2]), Integer.parseInt(parts[3])),
                firebase.firestore().document(path));
      } catch (Exception e) {
        throw new RoomProblem(RoomProblem.Code.INVALID_CURSOR, "Cursor inválido");
      }
    }
    var docs =
        await("rooms.list." + (memberScope ? "member" : "owned"), query.limit(limit + 1).get())
            .getDocuments();
    var items = new ArrayList<Room>();
    for (var doc : docs.subList(0, Math.min(limit, docs.size()))) {
      if (memberScope) {
        var ref = doc.getReference().getParent().getParent();
        var snapshot = await(ref.get());
        if (snapshot.exists()) {
          var room = read(snapshot);
          if ("ACTIVE".equals(room.status())) items.add(room);
        }
      } else items.add(read(doc));
    }
    String next = null;
    if (docs.size() > limit) {
      var last = docs.get(limit - 1);
      var time = last.getTimestamp(memberScope ? "joinedAt" : "createdAt");
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (uid
                          + "|"
                          + (memberScope ? "member" : "owned")
                          + "|"
                          + time.getSeconds()
                          + "|"
                          + time.getNanos()
                          + "|"
                          + last.getReference().getPath())
                      .getBytes(StandardCharsets.UTF_8));
    }
    return new Page(items, next);
  }

  private Map<String, Object> data(Room room) {
    return Map.of(
        "name",
        room.name(),
        "ownerUid",
        room.ownerUid(),
        "status",
        room.status(),
        "createdAt",
        stamp(room.createdAt()),
        "updatedAt",
        stamp(room.updatedAt()));
  }

  private Timestamp stamp(Instant i) {
    return Timestamp.ofTimeSecondsAndNanos(i.getEpochSecond(), i.getNano());
  }

  private Instant instant(Timestamp t) {
    return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
  }

  private Room read(DocumentSnapshot doc) {
    if (!doc.exists()) throw new RoomProblem(RoomProblem.Code.ROOM_NOT_FOUND, "Sala no encontrada");
    return new Room(
        doc.getId(),
        doc.getString("name"),
        doc.getString("ownerUid"),
        doc.getString("status"),
        instant(doc.getTimestamp("createdAt")),
        instant(doc.getTimestamp("updatedAt")));
  }

  private <T> T await(ApiFuture<T> f) {
    return await("rooms", f);
  }

  private <T> T await(String operation, ApiFuture<T> f) {
    try {
      return f.get(15, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      for (Throwable c = e; c != null; c = c.getCause()) if (c instanceof RoomProblem p) throw p;
      // Keep Firebase's index/IAM/timeout cause in server logs, never in the HTTP response.
      // Labels contain no user id, token, cursor or room document data.
      LOG.errorf(e, "Firestore operation=%s failed", operation);
      throw new DependencyUnavailableException(e);
    } finally {
      if (!f.isDone()) f.cancel(true);
    }
  }
}
