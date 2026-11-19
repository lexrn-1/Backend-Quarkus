package com.example.realtime.domain.collaboration;

public class CollaborationProblem extends RuntimeException {
  private final String code;

  public CollaborationProblem(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
