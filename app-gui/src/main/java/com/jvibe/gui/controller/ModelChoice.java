package com.jvibe.gui.controller;

public record ModelChoice(String provider, String providerName, String model) {
  public String display() {
    return model;
  }
}
