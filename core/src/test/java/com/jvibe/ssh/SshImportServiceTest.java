package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.*;
import com.jvibe.process.ProcessRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshImportServiceTest {
  @TempDir Path home;

  @Test void parsesPuttySessionAndSkipsNonSshProtocols() {
    var connection = SshImportService.parsePuttySession("prod", List.of(
        "HostName=server", "UserName=deploy", "PortNumber=2222", "PublicKeyFile=/keys/prod.ppk")).orElseThrow();
    assertEquals("server", connection.host());
    assertEquals(2222, connection.port());
    assertEquals("/keys/prod.ppk", connection.keyPath());
    assertEquals("Prod Web 01", SshImportService.parsePuttySession("Prod%20Web%2001", List.of("HostName=h"))
        .orElseThrow().name());
    assertTrue(SshImportService.parsePuttySession("telnet", List.of("HostName=server", "Protocol=telnet")).isEmpty());
  }

  @Test void parsesWindowsRegistryHexPorts() {
    var text = "HKEY_CURRENT_USER\\Software\\SimonTatham\\PuTTY\\Sessions\\prod\n"
        + "    HostName    REG_SZ    server\n    PortNumber    REG_DWORD    0x8ae\n"
        + "    UserName    REG_SZ    deploy\n    PublicKeyFile    REG_SZ    C:\\keys\\prod.ppk\n";
    var connections = SshImportService.parseWindowsRegistry(text);
    assertEquals(1, connections.size());
    assertEquals(2222, connections.get(0).port());
    assertEquals("deploy", connections.get(0).user());
  }

  @Test void importsPuttyFilesAndMergesStableIds() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
    var folder = Files.createDirectories(home.resolve(".putty/sessions"));
    Files.writeString(folder.resolve("prod"), "HostName=server\nUserName=deploy\n");
    Files.createDirectories(home.resolve(".ssh"));
    Files.writeString(home.resolve(".ssh/config"), "Host other\nHostName ignored\n");
    var service = new SshImportService(home, new ProcessRunner());
    var first = service.importAll();
    assertEquals(1, first.connections().size());
    assertEquals("prod", first.connections().get(0).name());
    assertEquals(first.connections().get(0).id(), service.importAll().connections().get(0).id());
  }
}
