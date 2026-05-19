plugins {
  java
}

group = "com.jvibe"
version = "0.1.0-SNAPSHOT"

val avajeInjectVersion = "12.5"
val avajeConfigVersion = "5.1"
val avajeJsonbVersion = "3.13"
val junitVersion = "5.13.4"
val mockitoVersion = "5.21.0"

subprojects {
  apply(plugin = "java")

  group = rootProject.group
  version = rootProject.version

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
    "testImplementation"("org.mockito:mockito-core:$mockitoVersion")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
  }

  configurations.configureEach {
    resolutionStrategy.failOnVersionConflict()
  }

  extra["avajeInjectVersion"] = avajeInjectVersion
  extra["avajeConfigVersion"] = avajeConfigVersion
  extra["avajeJsonbVersion"] = avajeJsonbVersion
}
