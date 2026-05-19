plugins {
  application
  id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
  implementation(project(":core"))
  runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

javafx {
  version = "21.0.11"
  modules = listOf("javafx.controls")
}

application {
  mainClass.set("com.jvibe.gui.JvibeGui")
}

tasks.jar {
  archiveBaseName.set("jvibe")
  manifest {
    attributes["Main-Class"] = application.mainClass.get()
  }
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
