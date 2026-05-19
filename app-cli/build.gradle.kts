plugins {
  application
}

dependencies {
  implementation(project(":core"))
  runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

application {
  mainClass.set("com.jvibe.cli.JvibeCli")
}

tasks.jar {
  archiveBaseName.set("jvibe-cli")
  manifest {
    attributes["Main-Class"] = application.mainClass.get()
  }
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
