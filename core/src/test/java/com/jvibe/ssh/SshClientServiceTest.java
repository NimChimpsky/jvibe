package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.Test;

class SshClientServiceTest {
  @Test
  void observesSharedCancellationBetweenShortChannelWaits() throws Exception {
    var session = mock(ClientSession.class);
    var channel = mock(ChannelExec.class);
    var opened = mock(OpenFuture.class);
    var cancelled = new AtomicBoolean();
    when(session.createExecChannel("sleep 30")).thenReturn(channel);
    when(channel.open()).thenReturn(opened);
    when(opened.verify(anyLong())).thenReturn(opened);
    when(channel.waitFor(any(), anyLong())).thenAnswer(invocation -> {
      assertTrue((long) invocation.getArgument(1) <= 100);
      cancelled.set(true);
      return EnumSet.of(ClientChannelEvent.TIMEOUT);
    });
    assertThrows(CancellationException.class,
        () -> SshClientService.execute(session, "sleep 30", Duration.ofSeconds(30), cancelled));
    verify(channel).close();
  }

  @Test
  void expiredTimeoutClosesChannel() throws Exception {
    var session = mock(ClientSession.class);
    var channel = mock(ChannelExec.class);
    var opened = mock(OpenFuture.class);
    when(session.createExecChannel("sleep 30")).thenReturn(channel);
    when(channel.open()).thenReturn(opened);
    when(opened.verify(anyLong())).thenReturn(opened);
    assertThrows(TimeoutException.class,
        () -> SshClientService.execute(session, "sleep 30", Duration.ZERO, new AtomicBoolean()));
    verify(channel).close();
  }

  @Test
  void alreadyCancelledDoesNotOpenChannel() {
    var session = mock(ClientSession.class);
    assertThrows(CancellationException.class,
        () -> SshClientService.execute(session, "pwd", Duration.ofSeconds(30), new AtomicBoolean(true)));
    verifyNoInteractions(session);
  }
}
