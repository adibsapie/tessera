package com.quorum.tessera.sync;

import com.quorum.tessera.api.model.ResendRequest;
import com.quorum.tessera.client.P2pClient;
import com.quorum.tessera.key.KeyManager;
import com.quorum.tessera.key.PublicKey;
import com.quorum.tessera.nacl.Key;
import java.util.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class TransactionRequesterTest {

    private static final PublicKey KEY_ONE = PublicKey.from(new byte[]{1});

    private static final PublicKey KEY_TWO = PublicKey.from(new byte[]{2});

    private KeyManager keyManager;

    private P2pClient p2pClient;

    private TransactionRequester transactionRequester;

    @Before
    public void init() {

        this.keyManager = mock(KeyManager.class);
        this.p2pClient = mock(P2pClient.class);

        doReturn(true).when(p2pClient).makeResendRequest(anyString(), any(ResendRequest.class));

        this.transactionRequester = new TransactionRequesterImpl(keyManager, p2pClient);
    }

    @After
    public void after() {
        verifyNoMoreInteractions(keyManager, p2pClient);
    }

    @Test
    public void noPublicKeysMakesNoCalls() {
        when(keyManager.getPublicKeys()).thenReturn(Collections.emptySet());

        this.transactionRequester.requestAllTransactionsFromNode("fakeurl.com");

        verifyZeroInteractions(p2pClient);
        verify(keyManager).getPublicKeys();
    }

    @Test
    public void multipleKeysMakesCorrectCalls() {
        
        final Set<Key> allKeys = Stream.of(KEY_ONE, KEY_TWO)
                .map(PublicKey::getKeyBytes)
                .map(Key::new).collect(Collectors.toSet());


        when(keyManager.getPublicKeys()).thenReturn(allKeys);
        
        this.transactionRequester.requestAllTransactionsFromNode("fakeurl1.com");

        final ArgumentCaptor<ResendRequest> captor = ArgumentCaptor.forClass(ResendRequest.class);
        verify(p2pClient, times(2)).makeResendRequest(eq("fakeurl1.com"), captor.capture());
        verify(keyManager).getPublicKeys();

        String encodedKeyOne = Base64.getEncoder().encodeToString(KEY_ONE.getKeyBytes());
        String encodedKeyTwo = Base64.getEncoder().encodeToString(KEY_TWO.getKeyBytes());
        
        assertThat(captor.getAllValues())
            .hasSize(2)
            .extracting("publicKey")
            .containsExactlyInAnyOrder(encodedKeyOne, encodedKeyTwo);
    }

    @Test
    public void failedCallRetries() {
        when(keyManager.getPublicKeys()).thenReturn(Collections.singleton(new Key(KEY_ONE.getKeyBytes())));
        
        when(p2pClient.makeResendRequest(anyString(), any(ResendRequest.class))).thenReturn(false)
                ;

        this.transactionRequester.requestAllTransactionsFromNode("fakeurl.com");

        verify(p2pClient, times(5)).makeResendRequest(eq("fakeurl.com"), any(ResendRequest.class));
        verify(keyManager).getPublicKeys();

    }

    @Test
    public void calltoPostDelegateThrowsException() {

        when(keyManager.getPublicKeys()).thenReturn(Collections.singleton(new Key(KEY_ONE.getKeyBytes())));
        when(p2pClient.makeResendRequest(anyString(), any(ResendRequest.class))).thenThrow(RuntimeException.class);
        
        this.transactionRequester.requestAllTransactionsFromNode("fakeurl.com");

        verify(p2pClient, times(5)).makeResendRequest(eq("fakeurl.com"), any(ResendRequest.class));
        verify(keyManager).getPublicKeys();

    }
}
