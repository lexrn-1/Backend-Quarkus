package com.example.realtime.infrastructure.adapter.out.persistence;

import com.example.realtime.application.port.out.CollaborationRepository;
import com.example.realtime.domain.collaboration.*;
import com.example.realtime.infrastructure.config.FirebaseProvider;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApplicationScoped
public class FirestoreCollaboration implements CollaborationRepository {
  @Inject FirebaseProvider firebase;

  private DocumentReference room(String id) {
    return firebase.firestore().collection("rooms").document(id);
  }

  public Access access(String uid, String id) {
    var room = await(room(id).get());
    if (!room.exists()) throw problem("ROOM_NOT_FOUND", "Sala no encontrada");
    if (!"ACTIVE".equals(room.getString("status")))
      throw problem("ROOM_CLOSED", "La sala está cerrada");
    if (!await(room.getReference().collection("members").document(uid).get()).exists())
      throw problem("FORBIDDEN", "No perteneces a esta sala");
    var profile = await(firebase.firestore().collection("users").document(uid).get());
    if (!profile.exists() || !"COMPLETE".equals(profile.getString("status")))
      throw problem("FORBIDDEN", "Perfil no disponible");
    return new Access(id, room.getString("name"), room.getString("ownerUid"), sender(uid, profile));
  }

  public Message persist(String uid, String id, String clientId, String text) {
    String key;
    try {
      key =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest((uid + "|" + clientId).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    var ref = room(id).collection("messages").document(key);
    await(
        firebase
            .firestore()
            .runTransaction(
                tx -> {
                  var room = tx.get(room(id)).get();
                  var member = tx.get(room(id).collection("members").document(uid)).get();
                  var user = tx.get(firebase.firestore().collection("users").document(uid)).get();
                  var old = tx.get(ref).get();
                  if (!room.exists()) throw problem("ROOM_NOT_FOUND", "Sala no encontrada");
                  if (!"ACTIVE".equals(room.getString("status")))
                    throw problem("ROOM_CLOSED", "Sala cerrada");
                  if (!member.exists()
                      || !user.exists()
                      || !"COMPLETE".equals(user.getString("status")))
                    throw problem("FORBIDDEN", "No tienes acceso a la sala");
                  if (old.exists()) {
                    if (!text.equals(old.getString("text")))
                      throw problem(
                          "MESSAGE_ID_CONFLICT", "El identificador ya corresponde a otro mensaje");
                    return true;
                  }
                  var author = sender(uid, user);
                  tx.create(
                      ref,
                      Map.of(
                          "roomId",
                          id,
                          "clientMessageId",
                          clientId,
                          "sender",
                          Map.of(
                              "uid",
                              uid,
                              "displayName",
                              author.displayName(),
                              "avatar",
                              author.avatar()),
                          "text",
                          text,
                          "createdAt",
                          FieldValue.serverTimestamp()));
                  return true;
                }));
    return message(id, await(ref.get()));
  }

  public Page history(String uid, String id, int limit, String before, String after) {
    access(uid, id);
    Query q = room(id).collection("messages").orderBy("createdAt").orderBy(FieldPath.documentId());
    if (before != null || after != null) {
      try {
        String[] fields =
            new String(
                    Base64.getUrlDecoder().decode(before != null ? before : after),
                    StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (fields.length != 4 || !id.equals(fields[0]) || !fields[3].matches("[a-f0-9]{64}"))
          throw new IllegalArgumentException();
        var stamp =
            Timestamp.ofTimeSecondsAndNanos(Long.parseLong(fields[1]), Integer.parseInt(fields[2]));
        var ref = room(id).collection("messages").document(fields[3]);
        q = before != null ? q.endBefore(stamp, ref) : q.startAfter(stamp, ref);
      } catch (Exception e) {
        throw problem("INVALID_CURSOR", "Cursor inválido para esta sala");
      }
    }
    var docs =
        await((after == null ? q.limitToLast(limit + 1) : q.limit(limit + 1)).get()).getDocuments();
    boolean more = docs.size() > limit;
    int start = more && after == null ? 1 : 0;
    int end = more && after != null ? limit : docs.size();
    var items = docs.subList(start, end).stream().map(d -> message(id, d)).toList();
    String next =
        more && !items.isEmpty()
            ? (after == null ? items.getFirst().cursor() : items.getLast().cursor())
            : null;
    return new Page(items, next);
  }

  public AutoCloseable observe(String id, Consumer<RoomChange> listener) {
    var registration =
        room(id)
            .addSnapshotListener(
                (snapshot, error) -> {
                  if (error != null) {
                    listener.accept(new RoomChange("", false, false));
                    return;
                  }
                  listener.accept(
                      new RoomChange(
                          snapshot != null && snapshot.exists() ? snapshot.getString("name") : "",
                          snapshot != null
                              && snapshot.exists()
                              && "ACTIVE".equals(snapshot.getString("status")),
                          true));
                });
    return registration::remove;
  }

  private Message.Sender sender(String uid, DocumentSnapshot user) {
    String avatar = user.getString("avatar");
    return new Message.Sender(
        uid,
        (Objects.toString(user.getString("firstName"), "")
                + " "
                + Objects.toString(user.getString("lastName"), ""))
            .strip(),
        avatar == null ? "avatar-1" : avatar);
  }

  private Message message(String id, DocumentSnapshot doc) {
    var data = (Map<?, ?>) doc.get("sender");
    var stamp = doc.getTimestamp("createdAt");
    String time =
        new DateTimeFormatterBuilder()
            .appendInstant(9)
            .toFormatter()
            .format(Instant.ofEpochSecond(stamp.getSeconds(), stamp.getNanos()));
    String cursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                (id + "|" + stamp.getSeconds() + "|" + stamp.getNanos() + "|" + doc.getId())
                    .getBytes(StandardCharsets.UTF_8));
    return new Message(
        doc.getId(),
        id,
        doc.getString("clientMessageId"),
        new Message.Sender(
            (String) data.get("uid"),
            (String) data.get("displayName"),
            (String) data.get("avatar")),
        doc.getString("text"),
        time,
        cursor);
  }

  private CollaborationProblem problem(String code, String message) {
    return new CollaborationProblem(code, message);
  }

  private <T> T await(ApiFuture<T> f) {
    try {
      return f.get(15, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      for (Throwable c = e; c != null; c = c.getCause())
        if (c instanceof CollaborationProblem p) throw p;
      throw problem("DEPENDENCY_UNAVAILABLE", "No pudimos acceder a los datos");
    } finally {
      if (!f.isDone()) f.cancel(true);
    }
  }
}
