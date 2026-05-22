package com.jvibe.gui.controller;

public record ModelChoice(String provider, String model) {
  public String display() {
    return provider + "/" + model;
  }
}
