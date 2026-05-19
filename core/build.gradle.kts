dependencies {
  implementation("io.avaje:avaje-inject:${property("avajeInjectVersion")}")
  implementation("io.avaje:avaje-config:${property("avajeConfigVersion")}")
  implementation("io.avaje:avaje-jsonb:${property("avajeJsonbVersion")}")
  implementation("org.slf4j:slf4j-api:2.0.17")

  testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
